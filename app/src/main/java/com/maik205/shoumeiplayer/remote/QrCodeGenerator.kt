package com.maik205.shoumeiplayer.remote

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object QrCodeGenerator {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun buildConnectionUri(host: String, port: Int, pin: String?): String {
        val base = "shoumei-remote://connect?host=$host&port=$port"
        return if (pin != null) "$base&pin=$pin" else base
    }

    fun encodeToMatrix(content: String, size: Int = 256): Array<BooleanArray>? = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        Array(width) { x ->
            BooleanArray(height) { y ->
                bitMatrix[x, y]
            }
        }
    }.getOrNull()
}

@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
) {
    val matrix = remember(content) { QrCodeGenerator.encodeToMatrix(content) } ?: return

    Canvas(modifier = modifier) {
        val matrixWidth = matrix.size
        val matrixHeight = matrix[0].size
        val cellWidth = size.width / matrixWidth
        val cellHeight = size.height / matrixHeight

        drawRect(color = lightColor)

        for (x in 0 until matrixWidth) {
            for (y in 0 until matrixHeight) {
                if (matrix[x][y]) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(x * cellWidth, y * cellHeight),
                        size = Size(cellWidth + 0.5f, cellHeight + 0.5f),
                    )
                }
            }
        }
    }
}
