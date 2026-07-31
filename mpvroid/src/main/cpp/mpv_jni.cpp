#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <mpv/client.h>

#include <atomic>
#include <chrono>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {
JavaVM *vm = nullptr;
mpv_handle *handle = nullptr;
jobject bridge = nullptr;
jobject surface = nullptr;
std::thread event_thread;
std::atomic<bool> running{false};
std::atomic<bool> initialized{false};
std::atomic<int64_t> last_timeline_dispatch_ms{0};
std::mutex lock;

const char *chars(JNIEnv *env, jstring value) {
    return value ? env->GetStringUTFChars(value, nullptr) : nullptr;
}

void release_chars(JNIEnv *env, jstring value, const char *value_chars) {
    if (value && value_chars) env->ReleaseStringUTFChars(value, value_chars);
}

JNIEnv *attach() {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    return vm->AttachCurrentThread(&env, nullptr) == JNI_OK ? env : nullptr;
}

void call_void(JNIEnv *env, const char *name, const char *signature, jvalue *args = nullptr) {
    jclass cls = env->GetObjectClass(bridge);
    jmethodID method = env->GetMethodID(cls, name, signature);
    if (method) env->CallVoidMethodA(bridge, method, args);
    env->DeleteLocalRef(cls);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

jstring string_or_empty(JNIEnv *env, const char *value) {
    return env->NewStringUTF(value ? value : "");
}

void dispatch_event(JNIEnv *env, mpv_event *event) {
    if (event->event_id == MPV_EVENT_PROPERTY_CHANGE) {
        auto *property = static_cast<mpv_event_property *>(event->data);
        // mpv can publish time-pos/cache-time much faster than a TV UI can consume it. Keep
        // state/track events immediate, but bound only the high-frequency timeline bridge before
        // allocating Java strings and crossing JNI. Kotlin still reads the latest exact value
        // directly from mpv for seeks and explicit progress reports.
        const bool high_frequency = property->name &&
            (strcmp(property->name, "time-pos") == 0 ||
             strcmp(property->name, "demuxer-cache-time") == 0);
        if (high_frequency) {
            const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
            const auto previous = last_timeline_dispatch_ms.load(std::memory_order_relaxed);
            if (now - previous < 50) return;
            last_timeline_dispatch_ms.store(now, std::memory_order_relaxed);
        }
        jstring name = string_or_empty(env, property->name);
        jvalue args[2]{};
        args[0].l = name;
        switch (property->format) {
            case MPV_FORMAT_INT64:
                args[1].j = property->data ? *static_cast<int64_t *>(property->data) : 0;
                call_void(env, "eventPropertyLong", "(Ljava/lang/String;J)V", args);
                break;
            case MPV_FORMAT_DOUBLE:
                args[1].d = property->data ? *static_cast<double *>(property->data) : 0.0;
                call_void(env, "eventPropertyDouble", "(Ljava/lang/String;D)V", args);
                break;
            case MPV_FORMAT_FLAG:
                args[1].z = property->data && *static_cast<int *>(property->data);
                call_void(env, "eventPropertyFlag", "(Ljava/lang/String;Z)V", args);
                break;
            case MPV_FORMAT_STRING: {
                auto **text = static_cast<char **>(property->data);
                jstring value = string_or_empty(env, text ? *text : nullptr);
                args[1].l = value;
                call_void(env, "eventPropertyString",
                          "(Ljava/lang/String;Ljava/lang/String;)V", args);
                env->DeleteLocalRef(value);
                break;
            }
            default:
                call_void(env, "eventProperty", "(Ljava/lang/String;)V", args);
                break;
        }
        env->DeleteLocalRef(name);
        return;
    }

    if (event->event_id == MPV_EVENT_LOG_MESSAGE) {
        auto *message = static_cast<mpv_event_log_message *>(event->data);
        jstring prefix = string_or_empty(env, message->prefix);
        jstring text = string_or_empty(env, message->text);
        jvalue args[3]{};
        args[0].l = prefix;
        args[1].i = message->log_level;
        args[2].l = text;
        call_void(env, "logMessage", "(Ljava/lang/String;ILjava/lang/String;)V", args);
        env->DeleteLocalRef(prefix);
        env->DeleteLocalRef(text);
        return;
    }

    jvalue arg{};
    arg.i = event->event_id;
    call_void(env, "event", "(I)V", &arg);
}

void event_loop(mpv_handle *ctx) {
    JNIEnv *env = attach();
    if (!env) return;
    while (running.load(std::memory_order_acquire)) {
        mpv_event *event = mpv_wait_event(ctx, -1);
        if (!event || event->event_id == MPV_EVENT_NONE) continue;
        dispatch_event(env, event);
        if (event->event_id == MPV_EVENT_SHUTDOWN) break;
    }
    vm->DetachCurrentThread();
}

mpv_handle *current() {
    std::lock_guard<std::mutex> guard(lock);
    return handle;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *java_vm, void *) {
    vm = java_vm;
    // mpv's Android audio/video paths ask FFmpeg for the process JavaVM.
    using SetJavaVm = int (*)(void *, void *);
    auto set_java_vm = reinterpret_cast<SetJavaVm>(dlsym(RTLD_DEFAULT, "av_jni_set_java_vm"));
    if (set_java_vm) set_java_vm(java_vm, nullptr);
    return JNI_VERSION_1_6;
}

#define JNI_METHOD(name) Java_com_maik205_mpvroid_MpvNative_##name

extern "C" JNIEXPORT void JNICALL JNI_METHOD(create)(JNIEnv *env, jobject self, jobject) {
    std::lock_guard<std::mutex> guard(lock);
    if (handle) return;
    bridge = env->NewGlobalRef(self);
    handle = mpv_create();
    if (!handle) {
        env->DeleteGlobalRef(bridge);
        bridge = nullptr;
        __android_log_print(ANDROID_LOG_ERROR, "MpvNative", "mpv_create failed");
    }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(init)(JNIEnv *, jobject) {
    if (initialized.load(std::memory_order_acquire)) return;
    auto *ctx = current();
    if (!ctx || mpv_initialize(ctx) < 0) return;
    initialized.store(true, std::memory_order_release);
    mpv_request_log_messages(ctx, "warn");
    running.store(true, std::memory_order_release);
    event_thread = std::thread(event_loop, ctx);
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(destroy)(JNIEnv *env, jobject) {
    mpv_handle *ctx;
    {
        std::lock_guard<std::mutex> guard(lock);
        ctx = handle;
        handle = nullptr;
    }
    if (!ctx) return;
    running.store(false, std::memory_order_release);
    initialized.store(false, std::memory_order_release);
    mpv_wakeup(ctx);
    if (event_thread.joinable()) event_thread.join();
    mpv_terminate_destroy(ctx);
    if (surface) {
        env->DeleteGlobalRef(surface);
        surface = nullptr;
    }
    if (bridge) {
        env->DeleteGlobalRef(bridge);
        bridge = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(attachSurface)(
    JNIEnv *env, jobject, jobject value) {
    auto *ctx = current();
    if (!ctx || !value) return;
    if (surface) env->DeleteGlobalRef(surface);
    surface = env->NewGlobalRef(value);
    int64_t window = reinterpret_cast<intptr_t>(surface);
    mpv_set_property(ctx, "wid", MPV_FORMAT_INT64, &window);
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(detachSurface)(JNIEnv *env, jobject) {
    auto *ctx = current();
    if (!ctx) return;
    int64_t detached = -1;
    mpv_set_property(ctx, "wid", MPV_FORMAT_INT64, &detached);
    if (surface) {
        env->DeleteGlobalRef(surface);
        surface = nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_METHOD(setOptionString)(
    JNIEnv *env, jobject, jstring name, jstring value) {
    auto *ctx = current();
    if (!ctx) return -1;
    const char *n = chars(env, name);
    const char *v = chars(env, value);
    int result = initialized.load(std::memory_order_acquire)
        ? mpv_set_property_string(ctx, n, v)
        : mpv_set_option_string(ctx, n, v);
    release_chars(env, value, v);
    release_chars(env, name, n);
    return result;
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(setPropertyString)(
    JNIEnv *env, jobject, jstring name, jstring value) {
    auto *ctx = current();
    if (!ctx) return;
    const char *n = chars(env, name);
    const char *v = chars(env, value);
    mpv_set_property_string(ctx, n, v);
    release_chars(env, value, v);
    release_chars(env, name, n);
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(setPropertyBoolean)(
    JNIEnv *env, jobject, jstring name, jboolean value) {
    auto *ctx = current();
    if (!ctx) return;
    const char *n = chars(env, name);
    int flag = value;
    mpv_set_property(ctx, n, MPV_FORMAT_FLAG, &flag);
    release_chars(env, name, n);
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(setPropertyDouble)(
    JNIEnv *env, jobject, jstring name, jdouble value) {
    auto *ctx = current();
    if (!ctx) return;
    const char *n = chars(env, name);
    double number = value;
    mpv_set_property(ctx, n, MPV_FORMAT_DOUBLE, &number);
    release_chars(env, name, n);
}

extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(getPropertyString)(
    JNIEnv *env, jobject, jstring name) {
    auto *ctx = current();
    if (!ctx) return nullptr;
    const char *n = chars(env, name);
    char *value = mpv_get_property_string(ctx, n);
    release_chars(env, name, n);
    if (!value) return nullptr;
    jstring result = env->NewStringUTF(value);
    mpv_free(value);
    return result;
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(observeProperty)(
    JNIEnv *env, jobject, jstring name, jint format) {
    auto *ctx = current();
    if (!ctx) return;
    const char *n = chars(env, name);
    mpv_observe_property(ctx, 0, n, static_cast<mpv_format>(format));
    release_chars(env, name, n);
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(command)(
    JNIEnv *env, jobject, jobjectArray values) {
    auto *ctx = current();
    if (!ctx || !values) return;
    jsize count = env->GetArrayLength(values);
    std::vector<std::string> owned;
    owned.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        const char *text = chars(env, value);
        owned.emplace_back(text ? text : "");
        release_chars(env, value, text);
        env->DeleteLocalRef(value);
    }
    std::vector<const char *> args;
    args.reserve(owned.size() + 1);
    for (const auto &value : owned) args.push_back(value.c_str());
    args.push_back(nullptr);
    mpv_command(ctx, args.data());
}
