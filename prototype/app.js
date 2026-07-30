const JELLYFIN_SPLASHSCREEN_PATH = "/Branding/Splashscreen";
let serverDefaultBackdrop = "";

const userLibraries = [
  { id: "cinema", name: "Cinema", icon: "movie" },
  { id: "shows", name: "Shows", icon: "tv" },
  { id: "music", name: "Music", icon: "headphones", view: "music" },
  { id: "photos", name: "Family photos", icon: "photo_library" },
  { id: "live", name: "Live TV", icon: "live_tv", view: "live" },
];

const mediaCatalog = [
  {
    title: "The Last Light",
    year: "2026",
    type: "Film",
    image: "",
    backdrop: "",
    hasLogo: true,
    meta: "2026|1h 52m|4K|Drama",
    summary: "A lighthouse keeper waits out the final storm of the season.",
  },
  {
    title: "Night Service",
    year: "2025",
    type: "Series",
    image: "./assets/poster-night-train.png",
    backdrop: "./assets/poster-night-train.png",
    hasLogo: true,
    meta: "2025|8 episodes|4K|Mystery",
    summary: "The last train carries one passenger who never bought a ticket.",
  },
  {
    title: "Solaris County",
    year: "2026",
    type: "Film",
    image: "./assets/poster-eclipse.png",
    backdrop: "./assets/poster-eclipse.png",
    hasLogo: true,
    meta: "2026|2h 04m|HDR|Science fiction",
    summary: "An observatory receives a signal from beneath the desert floor.",
  },
  {
    title: "Still Water",
    year: "2024",
    type: "Film",
    image: "https://picsum.photos/seed/still-water-cinema/600/900",
    backdrop: "https://picsum.photos/seed/still-water-cinema/1600/900",
    meta: "2024|1h 43m|HD|Thriller",
    summary: "A diver finds a town preserved beneath a mountain reservoir.",
  },
  {
    title: "Orchard Road",
    year: "2025",
    type: "Series",
    image: "https://picsum.photos/seed/orchard-road-film/600/900",
    backdrop: "https://picsum.photos/seed/orchard-road-film/1600/900",
    meta: "2025|6 episodes|4K|Drama",
    summary: "Three siblings return home to settle an impossible inheritance.",
  },
  {
    title: "The Quiet Season",
    year: "2023",
    type: "Film",
    image: "https://picsum.photos/seed/quiet-season-film/600/900",
    backdrop: "https://picsum.photos/seed/quiet-season-film/1600/900",
    meta: "2023|1h 37m|HD|Documentary",
    summary: "A fishing village prepares for a winter without daylight.",
  },
  {
    title: "Signal Fire",
    year: "2026",
    type: "Film",
    image: "https://picsum.photos/seed/signal-fire-film/600/900",
    backdrop: "https://picsum.photos/seed/signal-fire-film/1600/900",
    meta: "2026|1h 56m|HDR|Adventure",
    summary: "Two climbers follow a chain of fires across an unmapped range.",
  },
  {
    title: "Low Country",
    year: "2025",
    type: "Film",
    image: "https://picsum.photos/seed/low-country-film/600/900",
    backdrop: "https://picsum.photos/seed/low-country-film/1600/900",
    meta: "2025|1h 49m|4K|Crime",
    summary: "A county detective follows a case into the flooded marsh.",
  },
  {
    title: "Afterimage",
    year: "2024",
    type: "Film",
    image: "https://picsum.photos/seed/afterimage-film/600/900",
    backdrop: "https://picsum.photos/seed/afterimage-film/1600/900",
    meta: "2024|2h 11m|HDR|Drama",
    summary: "A photographer discovers a stranger appearing in every lost frame.",
  },
  {
    title: "Paper Moon",
    year: "2026",
    type: "Series",
    image: "https://picsum.photos/seed/paper-moon-series/600/900",
    backdrop: "https://picsum.photos/seed/paper-moon-series/1600/900",
    meta: "2026|10 episodes|HD|Comedy",
    summary: "A small-town newspaper invents a story that becomes inconveniently real.",
  },
  {
    title: "World News",
    year: "Live",
    type: "Live TV",
    image: "https://picsum.photos/seed/live-world-news/600/900",
    backdrop: "https://picsum.photos/seed/live-world-news/1600/900",
    meta: "Live now|HD|News",
    summary: "Live coverage from your selected news channel.",
  },
  {
    title: "Blue Hours",
    year: "2026",
    type: "Album",
    image: "https://picsum.photos/seed/blue-hours-album/600/600",
    backdrop: "https://picsum.photos/seed/blue-hours-album/1200/1200",
    meta: "Album|42 min|Lossless",
    summary: "Blue Hours by Marin Vale.",
  },
  {
    title: "The Cartographer's Sleep",
    year: "2025",
    type: "Audiobook",
    image: "https://picsum.photos/seed/cartographer-audiobook/600/600",
    backdrop: "https://picsum.photos/seed/cartographer-audiobook/1200/1200",
    meta: "Audiobook|8h 14m|Chapter 12",
    summary: "Continue the audiobook from chapter twelve.",
  },
  {
    title: "Saigon in July",
    year: "2026",
    type: "Photo album",
    image: "https://picsum.photos/seed/saigon-july-photos/600/900",
    backdrop: "https://picsum.photos/seed/saigon-july-photos/1600/900",
    meta: "Photo album|84 photos|2026",
    summary: "A photo album from July in Saigon.",
  },
];

const overflowCatalog = [
  ["Glass Harbor", "2026", "Film", "2026|1h 48m|4K|Drama", "A harbor pilot follows a light that should not be there.", "glass-harbor"],
  ["The Long Meridian", "2025", "Film", "2025|2h 06m|HDR|Adventure", "A survey team crosses the last uncharted line on the map.", "long-meridian"],
  ["Ashline", "2024", "Film", "2024|1h 41m|HD|Thriller", "A wildfire lookout hears a voice from an abandoned station.", "ashline"],
  ["Between Tides", "2026", "Film", "2026|1h 55m|4K|Drama", "Two families share one island and a disappearing road.", "between-tides"],
  ["Northbound", "2023", "Film", "2023|1h 39m|HD|Road movie", "A night bus continues north after every road closes.", "northbound"],
  ["Pale River", "2025", "Film", "2025|1h 46m|HDR|Mystery", "A dry river reveals the outline of a missing neighborhood.", "pale-river"],
  ["Static Bloom", "2024", "Film", "2024|1h 58m|4K|Science fiction", "A botanist finds flowers growing inside a radio tower.", "static-bloom"],
  ["Winter Signal", "2026", "Film", "2026|2h 01m|4K|Thriller", "A mountain relay station receives tomorrow's emergency calls.", "winter-signal"],
  ["Cedar House", "2026", "Series", "2026|8 episodes|4K|Drama", "Four tenants inherit a house with one locked floor.", "cedar-house"],
  ["Fault Lines", "2025", "Series", "2025|10 episodes|HDR|Thriller", "An earthquake exposes a buried rail system beneath the city.", "fault-lines"],
  ["The Fourth Room", "2024", "Series", "2024|6 episodes|HD|Mystery", "A hotel clerk discovers a room absent from every floor plan.", "fourth-room"],
  ["Salt Road", "2026", "Series", "2026|7 episodes|4K|Drama", "A coastal family rebuilds its trade route after a storm.", "salt-road"],
  ["Night Orchard", "2025", "Series", "2025|9 episodes|HDR|Crime", "A detective follows stolen fruit into a borderland network.", "night-orchard"],
  ["Common Ground", "2023", "Series", "2023|12 episodes|HD|Comedy", "Six neighbors attempt to share one impossible garden.", "common-ground"],
  ["The Weather Room", "2026", "Series", "2026|8 episodes|4K|Science fiction", "Forecasters begin recording storms that never arrive.", "weather-room"],
  ["Second Crossing", "2024", "Series", "2024|5 episodes|HD|Drama", "A ferry captain recognizes every passenger except herself.", "second-crossing"],
  ["Soft Current", "2026", "Album", "Album|38 min|Lossless", "Soft Current by Jun Vale.", "soft-current"],
  ["Nocturne Transit", "2025", "Album", "Album|47 min|Lossless", "Nocturne Transit by Glass Index.", "nocturne-transit"],
  ["Open Frequency", "2024", "Playlist", "Playlist|28 tracks|Lossless", "A wide-open mix of ambient and electronic music.", "open-frequency"],
  ["Field Recordings", "2026", "Album", "Album|54 min|Lossless", "Field recordings gathered along the northern coast.", "field-recordings"],
  ["Weather Systems", "2025", "Artist", "Artist|4 albums|Lossless", "Music by Weather Systems.", "weather-systems"],
  ["The Quiet Atlas", "2024", "Audiobook", "Audiobook|11h 06m|Chapter 4", "Continue The Quiet Atlas from chapter four.", "quiet-atlas"],
  ["After Midnight", "2026", "Playlist", "Playlist|41 tracks|Lossless", "A late-night playlist with a slow pulse.", "after-midnight"],
  ["Quiet Roads", "2025", "Playlist", "Playlist|22 tracks|Lossless", "A quiet playlist for long evening drives.", "quiet-roads"],
  ["Sunday Room", "2026", "Playlist", "Playlist|35 tracks|Lossless", "Soft records for a slow Sunday morning.", "sunday-room"],
  ["Deep Focus", "2024", "Playlist", "Playlist|52 tracks|Lossless", "Instrumental music for uninterrupted focus.", "deep-focus"],
  ["Marin Vale", "2023", "Artist", "Artist|6 albums|Lossless", "Music and live sessions by Marin Vale.", "marin-vale"],
  ["First Summer", "2026", "Photo album", "Photo album|112 photos|2026", "The first summer at the lake house.", "first-summer"],
  ["North Coast", "2025", "Photo album", "Photo album|126 photos|2025", "A photo album from the north coast.", "north-coast-photos"],
  ["Family Archive", "2024", "Photo album", "Photo album|302 photos|Archive", "Scanned photographs from the family archive.", "family-archive-photos"],
  ["Rainy Season", "2026", "Photo album", "Photo album|67 photos|2026", "Street photographs from the rainy season.", "rainy-season"],
  ["Mountain Week", "2025", "Photo album", "Photo album|94 photos|2025", "A week of trails, cabins, and morning fog.", "mountain-week"],
  ["Old Neighborhood", "2023", "Photo album", "Photo album|148 photos|2023", "A walk through familiar streets before they changed.", "old-neighborhood"],
  ["Tet Morning", "2026", "Photo album", "Photo album|76 photos|2026", "Family photographs from the first morning of Tet.", "tet-morning"],
  ["Film Scans", "2024", "Photo album", "Photo album|215 photos|Archive", "Recently restored rolls from the film archive.", "film-scans"],
];

mediaCatalog.push(
  ...overflowCatalog.map(([title, year, type, meta, summary, seed]) => ({
    title,
    year,
    type,
    meta,
    summary,
    image: `https://picsum.photos/seed/${seed}/600/900`,
    backdrop: `https://picsum.photos/seed/${seed}/1600/900`,
  })),
);

const nightServiceSeasons = [
  {
    id: "season-1",
    label: "Season 1",
    episodes: [
      {
        number: "S1 E1",
        title: "Last Stop",
        duration: "49 min",
        date: "7 Mar 2025",
        summary: "A late-night conductor finds a passenger listed on no manifest.",
        image: "https://picsum.photos/seed/night-service-episode-1/960/540",
        progress: 100,
      },
      {
        number: "S1 E2",
        title: "The Empty Platform",
        duration: "46 min",
        date: "14 Mar 2025",
        summary: "A station that closed thirty years ago appears beyond the fog.",
        image: "https://picsum.photos/seed/night-service-episode-2/960/540",
        progress: 100,
      },
      {
        number: "S1 E3",
        title: "Ticket, Please",
        duration: "51 min",
        date: "21 Mar 2025",
        summary: "Mara checks the final carriage and hears her own voice answer.",
        image: "https://picsum.photos/seed/night-service-episode-3/960/540",
        progress: 100,
      },
      {
        number: "S1 E4",
        title: "Passenger Zero",
        duration: "47 min",
        date: "28 Mar 2025",
        summary: "Mara follows an unclaimed suitcase into the train's sealed rear carriage.",
        image: "https://picsum.photos/seed/night-service-episode-4/960/540",
        progress: 42,
      },
      {
        number: "S1 E5",
        title: "Red Signal",
        duration: "45 min",
        date: "4 Apr 2025",
        summary: "Every signal turns red as the train approaches the old border.",
        image: "https://picsum.photos/seed/night-service-episode-5/960/540",
        progress: 0,
      },
      {
        number: "S1 E6",
        title: "The Sleeper",
        duration: "52 min",
        date: "11 Apr 2025",
        summary: "A passenger wakes with memories from the line's final journey.",
        image: "https://picsum.photos/seed/night-service-episode-6/960/540",
        progress: 0,
      },
      {
        number: "S1 E7",
        title: "No Return",
        duration: "48 min",
        date: "18 Apr 2025",
        summary: "The crew must choose which side of the tunnel is real.",
        image: "https://picsum.photos/seed/night-service-episode-7/960/540",
        progress: 0,
      },
      {
        number: "S1 E8",
        title: "Morning Service",
        duration: "56 min",
        date: "25 Apr 2025",
        summary: "The first light reveals who has been driving the train.",
        image: "https://picsum.photos/seed/night-service-episode-8/960/540",
        progress: 0,
      },
    ],
  },
  {
    id: "specials",
    label: "Specials",
    episodes: [
      {
        number: "S0 E1",
        title: "Behind the Line",
        duration: "24 min",
        date: "2 May 2025",
        summary: "The cast and crew retrace the route that inspired Night Service.",
        image: "https://picsum.photos/seed/night-service-special-1/960/540",
        progress: 0,
      },
      {
        number: "S0 E2",
        title: "The Signal Room",
        duration: "18 min",
        date: "9 May 2025",
        summary: "A close look at the practical sets and miniature railway.",
        image: "https://picsum.photos/seed/night-service-special-2/960/540",
        progress: 0,
      },
    ],
  },
];

const liveGuideChannels = [
  {
    number: "12",
    name: "World News",
    mark: ["World", "News"],
    programs: [
      { title: "Global Report", time: "20:30–21:30", status: "Live · 42 min left", rating: "TV-PG", genre: "News", span: 2, state: "now", progress: 31, summary: "News and analysis from correspondents around the world.", backdrop: "https://picsum.photos/seed/world-report-live/1600/900" },
      { title: "Markets Tonight", time: "21:30–22:00", status: "Starts in 42 min", rating: "TV-G", genre: "News", span: 1, state: "future", summary: "The day's markets, currencies, and business stories.", backdrop: "https://picsum.photos/seed/markets-tonight-live/1600/900" },
      { title: "The Brief", time: "22:00–22:30", status: "Starts in 1h 12m", rating: "TV-PG", genre: "News", span: 1, state: "future", summary: "A concise review of the stories shaping tomorrow.", backdrop: "https://picsum.photos/seed/the-brief-live/1600/900" },
    ],
  },
  {
    number: "18",
    name: "Vista One",
    mark: ["Vista", "One"],
    programs: [
      { title: "Wild Coast", time: "20:00–21:00", status: "Live · 12 min left", rating: "TV-G", genre: "Documentary", span: 2, state: "now", progress: 79, summary: "Following the wildlife that returns with the evening tide.", backdrop: "https://picsum.photos/seed/wild-coast-live/1600/900" },
      { title: "The Long Walk", time: "21:00–22:00", status: "Starts in 12 min", rating: "TV-PG", genre: "Travel", span: 2, state: "future", summary: "A journey on foot through the northern highlands.", backdrop: "https://picsum.photos/seed/long-walk-live/1600/900" },
    ],
  },
  {
    number: "24",
    name: "Arena",
    mark: ["Arena", "24"],
    programs: [
      { title: "Matchday Live", time: "20:00–21:30", status: "Live · 42 min left", rating: "TV-G", genre: "Sports", span: 3, state: "now", progress: 58, summary: "Live coverage, analysis, and interviews from tonight's match.", backdrop: "https://picsum.photos/seed/matchday-live-sport/1600/900" },
      { title: "Post-match", time: "21:30–22:00", status: "Starts in 42 min", rating: "TV-G", genre: "Sports", span: 1, state: "future", summary: "Reaction from the players and coaching staff.", backdrop: "https://picsum.photos/seed/post-match-live/1600/900" },
    ],
  },
  {
    number: "31",
    name: "Studio 8",
    mark: ["Studio", "Eight"],
    programs: [
      { title: "Kitchen Table", time: "20:30–21:00", status: "Live · 12 min left", rating: "TV-G", genre: "Lifestyle", span: 1, state: "now", progress: 61, summary: "Seasonal cooking from a small neighborhood kitchen.", backdrop: "https://picsum.photos/seed/kitchen-table-live/1600/900" },
      { title: "City Rooms", time: "21:00–22:00", status: "Starts in 12 min", rating: "TV-PG", genre: "Home", span: 2, state: "future", summary: "Designers rethink compact homes across the city.", backdrop: "https://picsum.photos/seed/city-rooms-live/1600/900" },
      { title: "Late Film", time: "22:00–22:30", status: "Starts in 1h 12m", rating: "TV-14", genre: "Film", span: 1, state: "future", summary: "A short film from an emerging regional director.", backdrop: "https://picsum.photos/seed/late-film-live/1600/900" },
    ],
  },
  {
    number: "45",
    name: "Kids",
    mark: ["Kids", "45"],
    programs: [
      { title: "Little Builders", time: "20:00–21:00", status: "Live · 12 min left", rating: "TV-Y", genre: "Children", span: 2, state: "now", progress: 83, summary: "Young makers solve everyday problems together.", backdrop: "https://picsum.photos/seed/little-builders-live/1600/900" },
      { title: "Moon Garden", time: "21:00–21:30", status: "Starts in 12 min", rating: "TV-Y7", genre: "Animation", span: 1, state: "future", summary: "Friends tend a garden that only blooms at night.", backdrop: "https://picsum.photos/seed/moon-garden-live/1600/900" },
      { title: "Story House", time: "21:30–22:00", status: "Starts in 42 min", rating: "TV-Y", genre: "Children", span: 1, state: "future", summary: "A new story opens a door to another world.", backdrop: "https://picsum.photos/seed/story-house-live/1600/900" },
    ],
  },
];

const app = document.querySelector("#app");
const content = document.querySelector("#content");
const ambientImage = document.querySelector("#ambient-image");
const heroTitle = document.querySelector("#hero-title");
const heroWordmark = document.querySelector("#hero-wordmark");
const heroMeta = document.querySelector("#hero-meta");
const heroSummary = document.querySelector("#hero-summary");
const detailLayer = document.querySelector("#detail-layer");
const personLayer = document.querySelector("#person-layer");
const personKnownRow = document.querySelector("#person-known-row");
const personCreditsList = document.querySelector("#person-credits-list");
const audioPlayerLayer = document.querySelector("#audio-player-layer");
const audioPlayerBackdrop = document.querySelector("#audio-player-backdrop");
const audioPlayerCover = document.querySelector("#audio-player-cover");
const audioPlayerContent = document.querySelector(".audio-player__content");
const audioLyricsPlayback = document.querySelector("#audio-lyrics-playback");
const audioPlayerContext = document.querySelector("#audio-player-context");
const audioPlayerContextContent = document.querySelector("#audio-player-context-content");
const audioContextColumns = document.querySelector("#audio-context-columns");
const audioUpNextContent = document.querySelector("#audio-up-next-content");
const audioSuggestedContent = document.querySelector("#audio-suggested-content");
const playerLayer = document.querySelector("#player-layer");
const playerDrawer = document.querySelector("#player-drawer");
const playerDrawerOptions = document.querySelector("#player-drawer-options");
const playerTimeline = document.querySelector("#player-timeline");
const playerTimelinePreview = document.querySelector("#player-timeline-preview");
const playerTimelinePreviewFrame = document.querySelector("#player-timeline-preview-frame");
const playerWordmark = document.querySelector("#player-wordmark");
const playerMiniSeek = document.querySelector("#player-mini-seek");
const postPlay = document.querySelector("#post-play");
const postPlayEpisodes = document.querySelector("#post-play-episodes");
const postPlayEpisodesList = document.querySelector("#post-play-episodes-list");
const playerRecovery = document.querySelector("#player-recovery");
const playerCheckin = document.querySelector("#player-checkin");
const stateLayer = document.querySelector("#state-layer");
const toast = document.querySelector("#toast");
const searchInput = document.querySelector("#search-input");
const searchResultGrid = document.querySelector("#search-result-grid");
const searchResultsTitle = document.querySelector("#search-results-title");
const libraryGrid = document.querySelector("#library-grid");
const libraryCount = document.querySelector("#library-count");
const settingsPanel = document.querySelector("#settings-panel");
const detailSelects = document.querySelector("#detail-selects");
const detailStaticFacts = document.querySelector("#detail-static-facts");
const detailWordmark = document.querySelector("#detail-wordmark");
const detailCover = document.querySelector("#detail-cover");
const detailSeries = document.querySelector("#detail-series");
const detailMusic = document.querySelector("#detail-music");
const detailTrackList = document.querySelector("#detail-track-list");
const detailMusicBody = document.querySelector(".detail-music__body");
const detailArtist = document.querySelector("#detail-artist");
const detailArtistPopularBody = document.querySelector("#detail-artist-popular-body");
const detailArtistTrackList = document.querySelector("#detail-artist-track-list");
const detailArtistReleaseRow = document.querySelector("#detail-artist-release-row");
const trackActions = document.querySelector("#track-actions");
const trackActionsTitle = document.querySelector("#track-actions-title");
const detailSeasonSelect = document.querySelector("#detail-season-select");
const detailEpisodeRow = document.querySelector("#detail-episode-row");
const detailRelatedRow = document.querySelector("#detail-related-row");
const detailAboutGrid = document.querySelector("#detail-about-grid");
const userLibraryNav = document.querySelector("#user-library-nav");
const libraryPageTitle = document.querySelector("#library-page-title");
const liveGuideGrid = document.querySelector("#live-guide-grid");
const liveBackdrop = document.querySelector("#live-backdrop");
const musicBackdrop = document.querySelector("#music-backdrop");
const musicHeroArt = document.querySelector("#music-hero-art");
const musicHeroArtist = document.querySelector("#music-hero-artist");
const musicHeroTitle = document.querySelector("#music-hero-title");
const musicHeroMeta = document.querySelector("#music-hero-meta");
const musicPreviewArt = document.querySelector("#music-preview-art");
const musicPreviewArtist = document.querySelector("#music-preview-artist");
const musicPreviewTitle = document.querySelector("#music-preview-title");
const musicPreviewMeta = document.querySelector("#music-preview-meta");
const connectionFailureMessages = [
  "Server didn’t respond",
  "Nothing answered",
  "Studio went quiet",
  "No reply from Studio",
  "Radio silence",
  "Knocked, no answer",
  "The server missed the call",
  "The connection wandered off",
  "Studio needs a moment",
  "That address stayed quiet",
  "No signal from Studio",
  "The network took a detour",
  "Studio may be asleep",
  "The server is hiding",
  "Couldn’t complete the handshake",
  "The lights are on, maybe",
  "Not home right now",
  "Lost the server for a moment",
  "Still trying to say hello",
  "One more try might do it",
];
const feedEndMessages = [
  "That's everything",
  "You're all caught up",
  "You found the bottom",
  "That's the whole shelf",
  "Nothing else down here",
  "You've seen the lot",
  "That's all for now",
  "End of the lineup",
  "Fresh out of rows",
  "The shelves end here",
  "Nothing hiding below",
  "That's the full collection",
  "End of the library",
  "No more titles down here",
  "You've reached the end",
  "That's the whole lot",
  "All done",
  "Roll credits",
  "Back to the top?",
  "Time to pick something",
];

let activeView = "connect";
let currentMedia = { ...mediaCatalog[0], kind: "Film" };
let previousFocus = null;
let backdropTimer = null;
let toastTimer = null;
let lastConnectionMessageIndex = -1;
let lastFeedEndMessageIndex = -1;
let activeLibraryId = userLibraries[0].id;
let activeSearchType = "all";
let activeLibraryView = "all";
let librarySortMode = "title";
let profileReturnView = "connect";
let activeSeriesData = null;
let currentEpisode = null;
let currentAudioTrack = null;
let personReturnFocus = null;
let personCredits = [];
let trackActionReturnFocus = null;
let trackActionContainer = null;
let audioPlayerPosition = 26;
let audioPlayerDuration = 252;
let audioPlayerReturnToDetails = false;
let audioPlayerContextMode = null;
let audioPlayerContextReturnFocus = null;
const audioContextViews = {
  "up-next": "tracks",
  suggested: "covers",
};
let playerPosition = 72;
let playerDrawerReturnFocus = null;
let playerDrawerType = null;
let playerDrawerHistory = [];
const playbackOptions = {
  speed: "Normal",
  aspect: "Fit",
  hdr: "Auto",
  videoTrack: "HEVC · Main 10",
  audioDelay: "0 ms",
  subtitleDelay: "0 ms",
  deinterlace: "Auto",
  sleepTimer: "Off",
};
const playbackDelayState = {
  audioDelay: { value: 0, min: -5000, max: 5000 },
  subtitleDelay: { value: 0, min: -5000, max: 5000 },
};
let playerReturnToDetails = false;
let playerMiniSeekTimer = null;
let playerExitTimer = null;
let postPlayNextItem = null;
let postPlayEpisodeSeason = null;
let playerRecoveryTimer = null;
let selectedLiveProgram = null;
let selectedMusicItem = null;
const focusMemory = new Map();

function rotateConnectionFailureMessage() {
  let nextIndex;
  do {
    nextIndex = Math.floor(Math.random() * connectionFailureMessages.length);
  } while (nextIndex === lastConnectionMessageIndex);
  lastConnectionMessageIndex = nextIndex;
  const heading = document.querySelector(".connection-error-status h2");
  if (heading) heading.textContent = connectionFailureMessages[nextIndex];
}

function rotateFeedEndMessage(viewName) {
  let nextIndex;
  do {
    nextIndex = Math.floor(Math.random() * feedEndMessages.length);
  } while (nextIndex === lastFeedEndMessageIndex);
  lastFeedEndMessageIndex = nextIndex;
  const copy = document.querySelector(`[data-view="${viewName}"] [data-feed-end-copy]`);
  if (copy) copy.textContent = feedEndMessages[nextIndex];
}

function libraryMediaCard(item) {
  const shape =
    activeLibraryId === "music"
      ? "square"
      : activeLibraryId === "photos"
        ? "landscape"
        : "poster";
  const button = document.createElement("button");
  button.className = `media-card media-card--${shape}`;
  button.dataset.focus = "";
  button.dataset.media = "";
  button.dataset.title = item.title;
  button.dataset.meta = item.meta;
  button.dataset.summary = item.summary;
  button.dataset.backdrop = item.backdrop;
  button.dataset.logo = item.hasLogo ? "available" : "none";
  button.innerHTML =
    shape === "landscape"
      ? `
        <img src="${item.backdrop}" alt="${item.title} artwork placeholder" />
        <span class="media-card__scrim"></span>
        <span class="media-card__body">
          <strong>${item.title}</strong>
          <small>${item.type} · ${item.year}</small>
        </span>
      `
      : `
        <img src="${item.image}" alt="${item.title} artwork placeholder" />
        <span class="media-card__label">
          <strong>${item.title}</strong>
          <small>${item.type} · ${item.year}</small>
        </span>
      `;
  return button;
}

function searchResultCard(item) {
  const button = document.createElement("button");
  button.className = "search-result";
  button.dataset.focus = "";
  button.dataset.media = "";
  button.dataset.title = item.title;
  button.dataset.meta = item.meta;
  button.dataset.summary = item.summary;
  button.dataset.backdrop = item.backdrop;
  button.dataset.logo = item.hasLogo ? "available" : "none";
  button.innerHTML = `
    <span class="search-result__image">
      <img src="${item.backdrop}" alt="${item.title} backdrop placeholder" />
    </span>
    <strong>${item.title}</strong>
    <small>${item.type} · ${item.year}</small>
  `;
  return button;
}

function populateMedia() {
  renderLibraryMedia();
  renderSearchResults();
}

function itemBelongsToLibrary(item) {
  const kind = inferMediaKind(item.meta);
  if (activeLibraryId === "cinema") return kind === "Film";
  if (activeLibraryId === "shows") return kind === "Series";
  if (activeLibraryId === "music") return mediaMode(kind) === "audio";
  if (activeLibraryId === "photos") return kind === "Photo album";
  return true;
}

function renderLibraryMedia() {
  const library = userLibraries.find((item) => item.id === activeLibraryId);
  const shape =
    activeLibraryId === "music"
      ? "square"
      : activeLibraryId === "photos"
        ? "landscape"
        : "poster";
  let items = mediaCatalog.filter(itemBelongsToLibrary);

  if (activeLibraryView === "recent") {
    items = items.filter((item) => Number(item.year) >= 2025);
  } else if (activeLibraryView === "favorites") {
    items = items.filter((_, index) => index % 2 === 0);
  }

  if (librarySortMode === "title") {
    items = [...items].sort((a, b) => a.title.localeCompare(b.title));
  } else {
    items = [...items].sort((a, b) => Number(b.year) - Number(a.year));
  }

  libraryPageTitle.textContent = library?.name || "Library";
  libraryCount.textContent = `${items.length} ${items.length === 1 ? "item" : "items"}`;
  libraryGrid.className = `library-grid library-grid--${shape}`;
  libraryGrid.replaceChildren(...items.map(libraryMediaCard));
}

function renderSearchResults() {
  const query = searchInput.value.trim().toLowerCase();
  const matches = mediaCatalog.filter((item) => {
    const kind = inferMediaKind(item.meta);
    const matchesQuery = `${item.title} ${item.type} ${item.year}`.toLowerCase().includes(query);
    const matchesType =
      activeSearchType === "all" ||
      (activeSearchType === "films" && kind === "Film") ||
      (activeSearchType === "series" && kind === "Series") ||
      (activeSearchType === "live" && ["Live TV", "Recording"].includes(kind)) ||
      (activeSearchType === "audio" && mediaMode(kind) === "audio") ||
      (activeSearchType === "photos" && kind === "Photo album");
    return matchesQuery && matchesType;
  });
  const visible = matches.slice(0, 12);
  searchResultsTitle.textContent = query ? "Results" : "Suggested";
  if (visible.length) {
    searchResultGrid.replaceChildren(...visible.map(searchResultCard));
  } else {
    const empty = document.createElement("p");
    empty.className = "search-empty";
    empty.textContent = "No matches";
    searchResultGrid.replaceChildren(empty);
  }
  document.querySelector("#result-count").textContent = `${visible.length} ${visible.length === 1 ? "title" : "titles"}`;
}

function selectLiveProgram(channelIndex, programIndex) {
  const channel = liveGuideChannels[channelIndex];
  const program = channel?.programs[programIndex];
  if (!channel || !program) return;
  selectedLiveProgram = { channel, program };
  liveBackdrop.style.backgroundImage = `url("${program.backdrop}")`;
  document.querySelector("#live-channel").textContent = `${channel.number} · ${channel.name}`;
  document.querySelector("#live-program-title").textContent = program.title;
  document.querySelector("#live-program-time").textContent = program.time;
  document.querySelector("#live-program-status").textContent = program.status;
  document.querySelector("#live-program-rating").textContent = program.rating;
  document.querySelector("#live-program-summary").textContent = program.summary;
  document.querySelector("#live-primary-label").textContent = program.state === "now" ? "Watch" : "Record";
  const channelMark = document.querySelector("#live-channel-mark");
  channelMark.innerHTML = `<span>${channel.mark[0]}</span><strong>${channel.mark[1]}</strong>`;
}

function renderLiveGuide() {
  const timeRow = document.createElement("div");
  timeRow.className = "guide-time-row";
  timeRow.innerHTML = `
    <span>Channel</span>
    <div>
      <time>20:00</time><time>20:30</time><time>21:00</time><time>21:30</time><time>22:00</time>
      <span class="guide-now-marker" aria-hidden="true"></span>
    </div>
  `;

  const rows = liveGuideChannels.map((channel, channelIndex) => {
    const row = document.createElement("div");
    row.className = "guide-row";
    const channelLabel = document.createElement("div");
    channelLabel.className = "guide-channel";
    channelLabel.innerHTML = `
      <span>${channel.number}</span>
      <strong>${channel.name}</strong>
    `;
    const programs = document.createElement("div");
    programs.className = "guide-row__programs";
    const nowMarker = document.createElement("span");
    nowMarker.className = "guide-now-marker";
    nowMarker.setAttribute("aria-hidden", "true");
    programs.append(nowMarker, ...channel.programs.map((program, programIndex) => {
      const button = document.createElement("button");
      button.className = "guide-program";
      button.classList.toggle("is-now", program.state === "now");
      button.dataset.focus = "";
      button.dataset.liveProgram = "";
      button.dataset.channelIndex = String(channelIndex);
      button.dataset.programIndex = String(programIndex);
      button.style.gridColumn = `span ${program.span}`;
      button.innerHTML = `
        <span><strong>${program.title}</strong><small>${program.time}</small></span>
        ${program.state === "now" ? `<i aria-hidden="true"><b style="width:${program.progress}%"></b></i>` : ""}
      `;
      return button;
    }));
    row.append(channelLabel, programs);
    return row;
  });
  liveGuideGrid.replaceChildren(timeRow, ...rows);
  selectLiveProgram(0, 0);
}

function selectMusicItem(element) {
  if (!element) return;
  selectedMusicItem = element;
  musicBackdrop.style.backgroundImage = `url("${element.dataset.backdrop}")`;
  musicHeroArt.src = element.dataset.image || element.querySelector("img")?.src || element.dataset.backdrop;
  musicHeroArtist.textContent = element.dataset.artist || "Music";
  musicHeroTitle.textContent = element.dataset.title;
  musicPreviewArt.src = musicHeroArt.src;
  musicPreviewArtist.textContent = musicHeroArtist.textContent;
  musicPreviewTitle.textContent = musicHeroTitle.textContent;
  const metaItems = element.dataset.meta.split("|");
  musicHeroMeta.replaceChildren(...metaItems.map((text) => {
    const span = document.createElement("span");
    span.textContent = text;
    return span;
  }));
  musicPreviewMeta.replaceChildren(...metaItems.map((text) => {
    const span = document.createElement("span");
    span.textContent = text;
    return span;
  }));
}

function settingRow(title, control) {
  return `
    <div class="setting-row">
      <strong>${title}</strong>
      ${control}
    </div>
  `;
}

function settingChoice(value, icon = "chevron_right", attributes = "") {
  return `
    <button class="setting-choice" data-focus ${attributes}>
      <span>${value}</span>
      <span class="material-symbols-rounded">${icon}</span>
    </button>
  `;
}

function settingToggle(label, enabled = true) {
  return `<button class="toggle${enabled ? " is-on" : ""}" data-focus aria-label="${label}"></button>`;
}

function renderSettings(tab = "playback") {
  const panels = {
    playback: `
      ${settingRow("Maximum streaming bitrate", settingChoice("Auto · 120 Mbps"))}
      ${settingRow("Refresh rate switching", settingChoice("Disabled"))}
      ${settingRow("Auto-play next episode", settingToggle("Toggle auto-play next episode"))}
      ${settingRow("Skip intro prompt", settingToggle("Toggle skip intro prompt"))}
      ${settingRow("Resume behavior", settingChoice("Ask"))}
      ${settingRow("Seek interval", settingChoice("10 seconds"))}
      ${settingRow("Remember playback speed", settingToggle("Toggle remembered playback speed", false))}
    `,
    video: `
      ${settingRow("Player core", '<span class="setting-value">mpv 0.41.0</span>')}
      ${settingRow("Rendering profile", settingChoice("Fast"))}
      ${settingRow("Video output", '<span class="setting-value">GPU</span>')}
      ${settingRow("Hardware decoding", settingChoice("MediaCodec copy"))}
      ${settingRow("Hardware codecs", settingChoice("Automatic"))}
      ${settingRow("HDR handling", settingChoice("Automatic"))}
      ${settingRow("Tone mapping", settingChoice("Automatic"))}
      ${settingRow("Deinterlacing", settingChoice("Automatic"))}
      ${settingRow("Frame interpolation", settingToggle("Toggle frame interpolation", false))}
    `,
    audio: `
      ${settingRow("Audio output", '<span class="setting-value">Android AudioTrack</span>')}
      ${settingRow("Preferred audio language", settingChoice("English"))}
      ${settingRow("Keep audio language for series", settingToggle("Toggle remembered series audio language"))}
      ${settingRow("Pitch correction", settingToggle("Toggle audio pitch correction"))}
      ${settingRow("Downmix to stereo", settingToggle("Toggle stereo downmix", false))}
      ${settingRow("Dolby Digital bitstream", settingToggle("Toggle Dolby Digital bitstream"))}
      ${settingRow("Dolby Digital Plus bitstream", settingToggle("Toggle Dolby Digital Plus bitstream"))}
      ${settingRow("DTS bitstream", settingToggle("Toggle DTS bitstream"))}
    `,
    subtitles: `
      ${settingRow("Renderer", '<span class="setting-value">libass</span>')}
      ${settingRow("Preferred subtitle language", settingChoice("English"))}
      ${settingRow("Subtitle mode", settingChoice("Smart"))}
      ${settingRow("Burn subtitles", settingChoice("Automatic"))}
      ${settingRow("Text size", settingChoice("100%"))}
      ${settingRow("Text color", settingChoice("White"))}
      ${settingRow("Text stroke", settingChoice("Medium"))}
      ${settingRow("Bold text", settingToggle("Toggle bold subtitles", false))}
      ${settingRow("Scale with window", settingToggle("Toggle subtitle scaling with the window"))}
      ${settingRow("Use video margins", settingToggle("Toggle subtitle video margins", false))}
      ${settingRow("PGS direct play", settingToggle("Toggle PGS subtitle direct play"))}
      ${settingRow("ASS and SSA direct play", settingChoice("Experimental"))}
    `,
    interface: `
      ${settingRow("Display language", settingChoice("English"))}
      ${settingRow("Interface scale", settingChoice("Comfortable"))}
      ${settingRow("Theme", settingChoice("Dark"))}
      ${settingRow("Backdrop images", settingToggle("Toggle backdrop images"))}
      ${settingRow("Backdrop rotation", settingChoice("20 seconds"))}
      ${settingRow("Watched indicators", settingToggle("Toggle watched indicators"))}
      ${settingRow("Show clock", settingToggle("Toggle interface clock"))}
      ${settingRow("Remember last library", settingToggle("Toggle remembered library"))}
    `,
    network: `
      ${settingRow("Maximum remote bitrate", settingChoice("Auto · 20 Mbps"))}
      ${settingRow("Stream cache", settingToggle("Toggle the mpv stream cache"))}
      ${settingRow("Cache duration", settingChoice("30 seconds"))}
      ${settingRow("Read ahead", settingChoice("20 seconds"))}
      ${settingRow("Forward cache limit", settingChoice("64 MiB"))}
      ${settingRow("Backward cache limit", settingChoice("32 MiB"))}
      ${settingRow("Resume buffer", settingChoice("1 second"))}
      ${settingRow("Network timeout", settingChoice("15 seconds"))}
      ${settingRow("Verify TLS certificates", settingToggle("Toggle TLS certificate verification", false))}
    `,
    screensaver: `
      ${settingRow("Start screensaver", settingChoice("After 5 minutes"))}
      ${settingRow("Content", settingChoice("All libraries"))}
      ${settingRow("Image duration", settingChoice("20 seconds"))}
      ${settingRow("Shuffle images", settingToggle("Toggle shuffled screensaver images"))}
      ${settingRow("Avoid repeats", settingToggle("Toggle screensaver repeat avoidance"))}
      ${settingRow("Show clock", settingToggle("Toggle screensaver clock"))}
    `,
    server: `
      ${settingRow("Server", '<span class="setting-value">Living room</span>')}
      ${settingRow("Address", '<span class="setting-value">192.168.1.14</span>')}
      ${settingRow("Connection", '<span class="setting-value">Secure</span>')}
      ${settingRow("Device name", '<span class="setting-value">Living room TV</span>')}
      ${settingRow("Test connection", settingChoice("Test", "network_check", "data-demo-toast"))}
      ${settingRow("Refresh libraries", settingChoice("Refresh", "refresh", "data-demo-toast"))}
      ${settingRow("Change server", settingChoice("Choose", "arrow_forward", 'data-nav="connect"'))}
    `,
    account: `
      ${settingRow("Profile", '<span class="setting-value">Maik</span>')}
      ${settingRow("Switch profile", settingChoice("Switch", "arrow_forward", "data-profile-switch"))}
      ${settingRow("Kids mode", settingToggle("Toggle kids mode", false))}
      ${settingRow("Login method", '<span class="setting-value">Password</span>')}
      ${settingRow("Quick Connect", '<button class="setting-choice" data-focus data-quick-connect><span class="login-quick__label">Generate</span><span class="material-symbols-rounded">key</span></button>')}
      ${settingRow("Sign out", settingChoice("Sign out", "logout"))}
    `,
  };
  settingsPanel.innerHTML = panels[tab];
}

function updateClock() {
  const now = new Date();
  document.querySelector("#clock").textContent = new Intl.DateTimeFormat([], {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(now);
  document.querySelector("#live-date").textContent = new Intl.DateTimeFormat([], {
    weekday: "long",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(now).replace(" at ", " · ");
}

function visibleFocusables() {
  const scope =
    !stateLayer.hidden
      ? stateLayer
      : !audioPlayerLayer.hidden
        ? audioPlayerLayer
        : !playerLayer.hidden
          ? playerLayer
          : !personLayer.hidden
            ? personLayer
          : !detailLayer.hidden
            ? detailLayer
            : document;
  return [...scope.querySelectorAll("[data-focus]")].filter((element) => {
    const style = getComputedStyle(element);
    return (
      !element.closest("[hidden]") &&
      style.visibility !== "hidden" &&
      style.display !== "none" &&
      element.getClientRects().length > 0
    );
  });
}

function focusElement(element) {
  if (!element) return;
  document.querySelectorAll(".is-key-focused").forEach((node) => node.classList.remove("is-key-focused"));
  element.classList.add("is-key-focused");
  element.focus({ preventScroll: true });
  const centerHomeRailVertically =
    activeView === "home" &&
    element.matches("[data-media]") &&
    element.closest(".media-section:not(.media-section--continue)");
  const centerLibraryGridVertically =
    activeView === "library" &&
    element.matches("[data-media]") &&
    element.closest(".library-grid");
  const centerDetailShelfVertically =
    !detailLayer.hidden &&
    Boolean(element.closest(".detail-shelf"));
  const centerDetailSeriesVertically =
    !detailLayer.hidden &&
    Boolean(element.closest(".series-next, .episode-row"));
  const centerDetailTrackVertically =
    !detailLayer.hidden &&
    Boolean(element.closest(".track-row:not(:first-child)"));
  const centerDetailArtistReleaseVertically =
    !detailLayer.hidden &&
    Boolean(element.closest(".detail-artist__releases"));
  const centerAudioLyricVertically =
    !audioPlayerLayer.hidden &&
    Boolean(element.closest(".audio-lyric"));
  const centerLiveGuideVertically =
    activeView === "live" &&
    Boolean(element.closest(".guide-row"));
  const snapMusicRail =
    activeView === "music" &&
    Boolean(element.closest(".music-shelf"));
  const snapDetailHero =
    !detailLayer.hidden &&
    Boolean(element.closest(".detail-selects, .detail-hero .hero__actions"));
  const snapDetailAudioList =
    !detailLayer.hidden &&
    Boolean(
      element.closest("[data-music-detail-shuffle]") ||
      element.matches(".track-row:first-child"),
    );
  const snapMusicTop =
    activeView === "music" &&
    Boolean(element.closest(".music-heading, .music-hero__actions"));
  if (snapDetailHero) {
    element.closest(".detail-hero").scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  } else if (snapDetailAudioList) {
    element.closest(".detail-music, .detail-artist")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  } else if (snapMusicTop) {
    document.querySelector('[data-view="music"]').scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  } else if (snapMusicRail) {
    element.scrollIntoView({
      behavior: "auto",
      block: "nearest",
      inline: "nearest",
    });
    element.closest(".music-shelf").scrollIntoView({
      behavior: "smooth",
      block: "start",
      inline: "nearest",
    });
  } else {
    element.scrollIntoView({
      behavior: "smooth",
      block:
        centerHomeRailVertically ||
        centerLibraryGridVertically ||
        centerDetailShelfVertically ||
        centerDetailSeriesVertically ||
        centerDetailTrackVertically ||
        centerDetailArtistReleaseVertically ||
        centerAudioLyricVertically ||
        centerLiveGuideVertically
          ? "center"
          : "nearest",
      inline: "nearest",
    });
  }
  const view = element.closest("[data-view]");
  if (view) focusMemory.set(view.dataset.view, element);
}

function setHomeBrowsing(enabled, restoreFocus = false, scrollToTop = true) {
  const homeView = document.querySelector('[data-view="home"]');
  homeView.classList.toggle("is-browsing", enabled);
  app.classList.toggle("is-media-browsing", enabled);
  if (!enabled) {
    if (restoreFocus) focusElement(homeView.querySelector(".hero__actions [data-focus]"));
    if (scrollToTop) {
      requestAnimationFrame(() => {
        homeView.querySelector(".hero").scrollIntoView({ behavior: "smooth", block: "start" });
      });
    }
  }
}

function setMusicBrowsing(enabled) {
  const musicView = document.querySelector('[data-view="music"]');
  musicView.classList.toggle("is-browsing", enabled);
  app.classList.toggle("is-media-browsing", enabled);
}

function searchFocusRows() {
  const searchView = document.querySelector('[data-view="search"]');
  const entryRow = [...searchView.querySelectorAll(".search-entry [data-focus]")];
  const typeRow = [...searchView.querySelectorAll(".search-types [data-focus]")];
  const resultRows = [];

  searchView.querySelectorAll(".search-result[data-focus]").forEach((result) => {
    const rowTop = result.offsetTop;
    const existingRow = resultRows.find((row) => Math.abs(row.top - rowTop) < 4);
    if (existingRow) {
      existingRow.items.push(result);
    } else {
      resultRows.push({ top: rowTop, items: [result] });
    }
  });

  resultRows.sort((a, b) => a.top - b.top);
  resultRows.forEach((row) => {
    row.items.sort((a, b) => a.offsetLeft - b.offsetLeft);
  });

  return [entryRow, typeRow, ...resultRows.map((row) => row.items)].filter((row) => row.length);
}

function moveSearchFocus(direction, current) {
  const rows = searchFocusRows();
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;

  const currentRow = rows[rowIndex];
  const columnIndex = currentRow.indexOf(current);
  let destination = current;

  if (direction === "left" && columnIndex > 0) {
    destination = currentRow[columnIndex - 1];
  } else if (direction === "right" && columnIndex < currentRow.length - 1) {
    destination = currentRow[columnIndex + 1];
  } else if (direction === "up" && rowIndex > 0) {
    const previousRow = rows[rowIndex - 1];
    destination =
      rowIndex === 1
        ? rows[0].at(-1)
        : previousRow[Math.min(columnIndex, previousRow.length - 1)];
  } else if (direction === "down" && rowIndex < rows.length - 1) {
    const nextRow = rows[rowIndex + 1];
    if (rowIndex === 0) {
      destination = nextRow.find((item) => item.classList.contains("is-active")) || nextRow[0];
    } else {
      destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
    }
  }

  focusElement(destination);
  return true;
}

function libraryFocusRows() {
  const libraryView = document.querySelector('[data-view="library"]');
  const controlRow = [...libraryView.querySelectorAll(".library-views [data-focus]")];
  const mediaRows = [];

  libraryView.querySelectorAll(".library-grid [data-focus]").forEach((item) => {
    const rowTop = item.offsetTop;
    const existingRow = mediaRows.find((row) => Math.abs(row.top - rowTop) < 4);
    if (existingRow) {
      existingRow.items.push(item);
    } else {
      mediaRows.push({ top: rowTop, items: [item] });
    }
  });

  mediaRows.sort((a, b) => a.top - b.top);
  mediaRows.forEach((row) => {
    row.items.sort((a, b) => a.offsetLeft - b.offsetLeft);
  });

  return [controlRow, ...mediaRows.map((row) => row.items)].filter((row) => row.length);
}

function moveLibraryFocus(direction, current) {
  const rows = libraryFocusRows();
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;

  const currentRow = rows[rowIndex];
  const columnIndex = currentRow.indexOf(current);
  let destination = current;

  if (direction === "left" && columnIndex > 0) {
    destination = currentRow[columnIndex - 1];
  } else if (direction === "right" && columnIndex < currentRow.length - 1) {
    destination = currentRow[columnIndex + 1];
  } else if (direction === "up") {
    if (rowIndex === 0) {
      destination = document.querySelector(`.top-nav__item[data-library="${activeLibraryId}"]`) || current;
    } else {
      const previousRow = rows[rowIndex - 1];
      destination = previousRow[Math.min(columnIndex, previousRow.length - 1)];
    }
  } else if (direction === "down") {
    if (rowIndex < rows.length - 1) {
      const nextRow = rows[rowIndex + 1];
      destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
    } else {
      rotateFeedEndMessage("library");
      document.querySelector(".library-end")?.scrollIntoView({ behavior: "smooth", block: "end" });
      return true;
    }
  }

  focusElement(destination);
  return true;
}

function moveSettingsFocus(direction, current) {
  const settingsView = document.querySelector('[data-view="settings"]');
  const categoryRow = [...settingsView.querySelectorAll("[data-settings-tab]")];
  const controlRows = [...settingsPanel.querySelectorAll("[data-focus]")].map((control) => [control]);
  const rows = [categoryRow, ...controlRows].filter((row) => row.length);
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;

  const currentRow = rows[rowIndex];
  const columnIndex = currentRow.indexOf(current);
  let destination = current;

  if (rowIndex === 0 && direction === "left" && columnIndex > 0) {
    destination = currentRow[columnIndex - 1];
  } else if (rowIndex === 0 && direction === "right" && columnIndex < currentRow.length - 1) {
    destination = currentRow[columnIndex + 1];
  } else if (direction === "up") {
    destination =
      rowIndex === 0
        ? document.querySelector(".topbar-settings")
        : rowIndex === 1
          ? categoryRow.find((item) => item.classList.contains("is-active")) || categoryRow[0]
          : rows[rowIndex - 1][0];
  } else if (direction === "down" && rowIndex < rows.length - 1) {
    destination = rows[rowIndex + 1][0];
  }

  focusElement(destination);
  return true;
}

function moveLiveFocus(direction, current) {
  if (activeView !== "live") return false;
  const liveView = document.querySelector('[data-view="live"]');
  if (!liveView.contains(current)) return false;
  const rows = [
    [...liveView.querySelectorAll(".live-views [data-focus]")],
    [...liveView.querySelectorAll(".live-hero__actions [data-focus]")],
    ...[...liveView.querySelectorAll(".guide-row")].map((row) =>
      [...row.querySelectorAll(".guide-program")]),
  ].filter((row) => row.length);
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;
  const columnIndex = rows[rowIndex].indexOf(current);
  let destination = current;

  if (direction === "left" && columnIndex > 0) {
    destination = rows[rowIndex][columnIndex - 1];
  } else if (direction === "right" && columnIndex < rows[rowIndex].length - 1) {
    destination = rows[rowIndex][columnIndex + 1];
  } else if (direction === "up") {
    if (rowIndex === 0) {
      destination = document.querySelector('[data-nav="live"]');
    } else {
      const previousRow = rows[rowIndex - 1];
      destination = previousRow[Math.min(columnIndex, previousRow.length - 1)];
    }
  } else if (direction === "down" && rowIndex < rows.length - 1) {
    const nextRow = rows[rowIndex + 1];
    destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
  }
  focusElement(destination);
  return true;
}

function moveMusicFocus(direction, current) {
  if (activeView !== "music") return false;
  const musicView = document.querySelector('[data-view="music"]');
  if (!musicView.contains(current)) return false;
  const rows = [
    [...musicView.querySelectorAll(".music-views [data-focus]")],
    [...musicView.querySelectorAll(".music-hero__actions [data-focus]")],
    ...[...musicView.querySelectorAll(".music-row")].map((row) =>
      [...row.querySelectorAll(".music-item")]),
  ].filter((row) => row.length);
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;
  const columnIndex = rows[rowIndex].indexOf(current);
  let destination = current;

  if (direction === "left" && columnIndex > 0) {
    destination = rows[rowIndex][columnIndex - 1];
  } else if (direction === "right" && columnIndex < rows[rowIndex].length - 1) {
    destination = rows[rowIndex][columnIndex + 1];
  } else if (direction === "up") {
    if (rowIndex === 0) {
      destination = document.querySelector('[data-nav="music"]');
    } else {
      const previousRow = rows[rowIndex - 1];
      destination = previousRow[Math.min(columnIndex, previousRow.length - 1)];
    }
  } else if (direction === "down") {
    if (rowIndex < rows.length - 1) {
      const nextRow = rows[rowIndex + 1];
      destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
    } else {
      document.querySelector(".music-end")?.scrollIntoView({ behavior: "smooth", block: "end" });
      return true;
    }
  }

  focusElement(destination);
  return true;
}

function movePersonFocus(direction, current) {
  if (personLayer.hidden || !personLayer.contains(current)) return false;
  const visible = visibleFocusables();
  const rows = [
    visible.filter((item) => item.matches("[data-close-person]")),
    visible.filter((item) => item.matches("[data-person-favorite]")),
    visible.filter((item) => item.closest("#person-known-row")),
    ...visible.filter((item) => item.matches("[data-person-credit]")).map((item) => [item]),
  ].filter((row) => row.length);
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;
  const row = rows[rowIndex];
  const columnIndex = row.indexOf(current);
  let destination = current;

  if (direction === "left" && columnIndex > 0) {
    destination = row[columnIndex - 1];
  } else if (direction === "right" && columnIndex < row.length - 1) {
    destination = row[columnIndex + 1];
  } else if (direction === "up" && rowIndex > 0) {
    const previousRow = rows[rowIndex - 1];
    destination = previousRow[Math.min(columnIndex, previousRow.length - 1)];
  } else if (direction === "down" && rowIndex < rows.length - 1) {
    const nextRow = rows[rowIndex + 1];
    destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
  }

  focusElement(destination);
  return true;
}

function moveDetailFocus(direction, current) {
  if (detailLayer.hidden || !detailLayer.contains(current)) return false;

  if (!trackActions.hidden && trackActions.contains(current)) {
    const actions = [...trackActions.querySelectorAll("[data-track-action]")];
    const actionIndex = actions.indexOf(current);
    if (direction === "left") {
      closeTrackActions(true);
    } else if (direction === "up" && actionIndex > 0) {
      focusElement(actions[actionIndex - 1]);
    } else if (direction === "down" && actionIndex < actions.length - 1) {
      focusElement(actions[actionIndex + 1]);
    }
    return true;
  }

  if (current.matches("[data-audio-track]") && direction === "right") {
    openTrackActions(current);
    return true;
  }

  const visible = visibleFocusables();
  const rows = [
    visible.filter((item) => item.matches("[data-close-detail]")),
    visible.filter((item) => item.closest(".detail-hero .hero__actions")),
    visible.filter((item) => item.closest(".detail-selects")),
    visible.filter((item) => item.matches(".series-next__art")),
    visible.filter((item) => item.matches("#detail-season-select")),
    visible.filter((item) => item.closest(".episode-row")),
    visible.filter((item) => item.matches("[data-music-detail-shuffle]")),
    ...visible.filter((item) => item.matches("[data-audio-track]")).map((item) => [item]),
    visible.filter((item) => item.closest(".artist-release-row")),
    visible.filter((item) => item.closest('[aria-labelledby="related-title"] .media-row')),
    visible.filter((item) => item.closest(".cast-row")),
  ].filter((row) => row.length);
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;

  const currentRow = rows[rowIndex];
  const columnIndex = currentRow.indexOf(current);
  let destination = current;

  if (direction === "left" && columnIndex > 0) {
    destination = currentRow[columnIndex - 1];
  } else if (direction === "right" && columnIndex < currentRow.length - 1) {
    destination = currentRow[columnIndex + 1];
  } else if (direction === "up" && rowIndex > 0) {
    const previousRow = rows[rowIndex - 1];
    destination = previousRow[Math.min(columnIndex, previousRow.length - 1)];
  } else if (direction === "down") {
    if (rowIndex < rows.length - 1) {
      const nextRow = rows[rowIndex + 1];
      destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
    } else {
      detailLayer.querySelector(".detail-about")?.scrollIntoView({ behavior: "smooth", block: "center" });
      return true;
    }
  }

  focusElement(destination);
  return true;
}

function moveAudioPlayerFocus(direction, current) {
  if (audioPlayerLayer.hidden || !audioPlayerLayer.contains(current)) return false;

  if (audioPlayerContextMode && audioPlayerContext.contains(current)) {
    const items = [...audioPlayerContextContent.querySelectorAll("[data-focus]")];
    const itemIndex = items.indexOf(current);
    if (direction === "left") {
      focusElement(
        audioLyricsPlayback.querySelector('[data-audio-player-control="primary"]'),
      );
    } else if (direction === "up" && itemIndex > 0) {
      focusElement(items[itemIndex - 1]);
    } else if (direction === "down" && itemIndex < items.length - 1) {
      focusElement(items[itemIndex + 1]);
    }
    return true;
  }

  const contextColumn = current.closest("[data-audio-context-column]");
  if (contextColumn) {
    const type = contextColumn.dataset.audioContextColumn;
    const view = contextColumn.dataset.presentation;
    const toggle = contextColumn.querySelector("[data-audio-context-view]");
    const items = [...contextColumn.querySelectorAll(".audio-context-column__content [data-focus]")];
    const otherColumn = audioContextColumns.querySelector(
      `[data-audio-context-column="${type === "up-next" ? "suggested" : "up-next"}"]`,
    );
    const otherToggle = otherColumn.querySelector("[data-audio-context-view]");
    const otherItems = [...otherColumn.querySelectorAll(".audio-context-column__content [data-focus]")];
    const itemIndex = items.indexOf(current);

    if (current === toggle) {
      if (direction === "left" || direction === "right") {
        focusElement(otherToggle);
      } else if (direction === "down") {
        focusElement(items[0]);
      } else if (direction === "up") {
        const playbackRoot = audioPlayerContextMode === "lyrics"
          ? audioLyricsPlayback
          : audioPlayerContent;
        setAudioBrowsing(
          false,
          playbackRoot.querySelector(
            type === "up-next"
              ? '[data-audio-player-control="queue"]'
              : '[data-audio-player-control="lyrics"]',
          ),
        );
      }
    } else if (view === "covers") {
      if (direction === "left" && itemIndex > 0) {
        focusElement(items[itemIndex - 1]);
      } else if (direction === "right" && itemIndex < items.length - 1) {
        focusElement(items[itemIndex + 1]);
      } else if (direction === "left" || direction === "right") {
        focusElement(otherItems[Math.min(itemIndex, otherItems.length - 1)] || otherToggle);
      } else if (direction === "up") {
        focusElement(toggle);
      }
    } else if (direction === "up") {
      focusElement(itemIndex > 0 ? items[itemIndex - 1] : toggle);
    } else if (direction === "down" && itemIndex < items.length - 1) {
      focusElement(items[itemIndex + 1]);
    } else if (direction === "left" || direction === "right") {
      focusElement(otherItems[Math.min(itemIndex, otherItems.length - 1)] || otherToggle);
    }
    return true;
  }

  if (
    audioPlayerContextMode === "lyrics" &&
    current.matches('[data-audio-player-control="lyrics"]') &&
    direction === "right"
  ) {
    focusElement(
      audioPlayerContextContent.querySelector(".is-current") ||
      audioPlayerContextContent.querySelector("[data-focus]"),
    );
    return true;
  }

  const activePlaybackRoot = audioPlayerContextMode === "lyrics"
    ? audioLyricsPlayback
    : audioPlayerContent;
  const activeAudioTimeline = activePlaybackRoot.querySelector("[data-audio-player-timeline]");
  const playbackRows = audioPlayerContextMode === "lyrics"
    ? [[...activePlaybackRoot.querySelectorAll(".audio-lyrics-playback__actions [data-focus]")]]
    : [
      [...activePlaybackRoot.querySelectorAll(".audio-transport [data-focus]")],
      [...activePlaybackRoot.querySelectorAll(".audio-tools [data-focus]")],
    ];
  const rows = [
    [...audioPlayerLayer.querySelectorAll("[data-close-audio-player]")],
    [activeAudioTimeline],
    ...playbackRows,
    ...(audioContextColumns.hidden
      ? []
      : [[...audioContextColumns.querySelectorAll("[data-audio-context-view]")]]),
  ].filter((row) => row.length);
  const rowIndex = rows.findIndex((row) => row.includes(current));
  if (rowIndex < 0) return false;
  const columnIndex = rows[rowIndex].indexOf(current);
  let destination = current;

  if (current === activeAudioTimeline && (direction === "left" || direction === "right")) {
    updateAudioPlayerTimeline(direction === "left" ? -4 : 4);
    return true;
  }

  if (direction === "left" && columnIndex > 0) {
    destination = rows[rowIndex][columnIndex - 1];
  } else if (direction === "right" && columnIndex < rows[rowIndex].length - 1) {
    destination = rows[rowIndex][columnIndex + 1];
  } else if (direction === "up" && rowIndex > 0) {
    const previousRow = rows[rowIndex - 1];
    destination = previousRow[Math.min(columnIndex, previousRow.length - 1)];
  } else if (direction === "down" && rowIndex < rows.length - 1) {
    const nextRow = rows[rowIndex + 1];
    destination = nextRow[Math.min(columnIndex, nextRow.length - 1)];
  }

  focusElement(destination);
  return true;
}

function movePlayerFocus(direction, current) {
  if (playerLayer.hidden || !playerLayer.contains(current)) return false;

  if (!postPlay.hidden) {
    if (!postPlayEpisodes.hidden) {
      const back = postPlayEpisodes.querySelector("[data-post-play-episodes-back]");
      const episodes = [...postPlayEpisodesList.querySelectorAll("[data-post-play-episode]")];
      if (current === back) {
        if ((direction === "down" || direction === "right") && episodes.length) {
          focusElement(episodes[0]);
        }
        return true;
      }

      const episodeIndex = episodes.indexOf(current);
      if (episodeIndex < 0) {
        focusElement(back);
      } else if (direction === "up") {
        focusElement(episodeIndex > 0 ? episodes[episodeIndex - 1] : back);
      } else if (direction === "down" && episodeIndex < episodes.length - 1) {
        focusElement(episodes[episodeIndex + 1]);
      } else if (direction === "left") {
        focusElement(back);
      }
      return true;
    }

    const actions = [...postPlay.querySelectorAll(".post-play__actions [data-focus]")]
      .filter((item) => !item.hidden);
    const index = actions.indexOf(current);
    if (index < 0) {
      focusElement(actions[0]);
      return true;
    }
    if (direction === "left" && index > 0) {
      focusElement(actions[index - 1]);
    } else if (direction === "right" && index < actions.length - 1) {
      focusElement(actions[index + 1]);
    }
    return true;
  }

  if (!playerDrawer.hidden) {
    const items = [
      playerDrawer.querySelector("[data-close-player-drawer]"),
      ...playerDrawerOptions.querySelectorAll("[data-focus]"),
    ].filter(Boolean);
    const index = items.indexOf(current);
    if (index < 0) return false;
    if (
      (current.matches("[data-delay-slider]") || current.matches("[data-delay-bound]")) &&
      (direction === "left" || direction === "right")
    ) {
      adjustDelayControl(current, direction);
    } else if (direction === "left") {
      backPlayerDrawer();
    } else if (direction === "right" && current.classList.contains("is-navigable")) {
      current.click();
    } else if (direction === "up" && index > 0) {
      focusElement(items[index - 1]);
    } else if (direction === "down" && index < items.length - 1) {
      focusElement(items[index + 1]);
    }
    return true;
  }

  if (!playerCheckin.hidden) {
    const actions = [...playerCheckin.querySelectorAll("[data-player-checkin-action]")];
    const index = actions.indexOf(current);
    if (index < 0) {
      focusElement(actions.find((item) => item.dataset.playerCheckinAction === "continue"));
    } else if (direction === "left" && index > 0) {
      focusElement(actions[index - 1]);
    } else if (direction === "right" && index < actions.length - 1) {
      focusElement(actions[index + 1]);
    }
    return true;
  }

  if (!playerRecovery.hidden) {
    const actions = [...playerRecovery.querySelectorAll("[data-player-recovery-action]")];
    const index = actions.indexOf(current);
    if (index < 0) {
      focusElement(actions[0]);
    } else if (direction === "left" && index > 0) {
      focusElement(actions[index - 1]);
    } else if (direction === "right" && index < actions.length - 1) {
      focusElement(actions[index + 1]);
    }
    return true;
  }

  const controls = [...playerLayer.querySelectorAll(".player-controls [data-focus]")]
    .filter((item) => getComputedStyle(item).display !== "none");
  if (current === playerTimeline && (direction === "left" || direction === "right")) {
    seekPlayerSeconds(direction === "left" ? -30 : 30);
    return true;
  }
  if (current === playerTimeline) {
    if (direction === "up") setPlayerOsdVisible(false);
    if (direction === "down") {
      focusElement(controls.find((item) => item.matches(".player-control--primary")) || controls[0]);
    }
    return true;
  }

  const index = controls.indexOf(current);
  if (index < 0) return false;
  if (direction === "left" && index > 0) {
    focusElement(controls[index - 1]);
  } else if (direction === "right" && index < controls.length - 1) {
    focusElement(controls[index + 1]);
  } else if (direction === "up") {
    setPlayerOsdVisible(false);
  }
  return true;
}

function moveFocus(direction) {
  const current = document.activeElement?.matches?.("[data-focus]") ? document.activeElement : visibleFocusables()[0];
  if (!current) return;

  if (moveAudioPlayerFocus(direction, current)) return;

  if (movePlayerFocus(direction, current)) return;

  if (movePersonFocus(direction, current)) return;

  if (moveDetailFocus(direction, current)) return;

  if (direction === "up" && activeView === "home" && current.closest(".media-section--continue")) {
    setHomeBrowsing(false, true);
    return;
  }

  if (activeView === "search" && moveSearchFocus(direction, current)) return;

  if (activeView === "library" && direction === "down" && current.closest(".top-nav")) {
    focusElement(document.querySelector(".library-view.is-active"));
    return;
  }

  if (activeView === "library" && moveLibraryFocus(direction, current)) return;

  if (activeView === "settings" && direction === "down" && current.closest(".topbar__actions")) {
    focusElement(document.querySelector(".settings-menu__item.is-active"));
    return;
  }

  if (activeView === "settings" && moveSettingsFocus(direction, current)) return;

  if (activeView === "live" && direction === "down" && current.closest(".top-nav")) {
    focusElement(document.querySelector(".live-view.is-active"));
    return;
  }

  if (moveLiveFocus(direction, current)) return;

  if (activeView === "music" && direction === "down" && current.closest(".top-nav")) {
    focusElement(document.querySelector(".music-view.is-active"));
    return;
  }

  if (moveMusicFocus(direction, current)) return;

  const activeViewElement = current.closest("[data-view]");
  const focusables = activeViewElement && app.classList.contains("app--onboarding")
    ? visibleFocusables().filter((element) => activeViewElement.contains(element))
    : visibleFocusables();
  const source = current.getBoundingClientRect();
  const sourceX = source.left + source.width / 2;
  const sourceY = source.top + source.height / 2;
  const horizontalMove = direction === "left" || direction === "right";
  const candidates = focusables
    .filter((element) => element !== current)
    .map((element) => {
      const rect = element.getBoundingClientRect();
      const x = rect.left + rect.width / 2;
      const y = rect.top + rect.height / 2;
      const dx = x - sourceX;
      const dy = y - sourceY;
      const valid =
        (direction === "left" && dx < -4) ||
        (direction === "right" && dx > 4) ||
        (direction === "up" && dy < -4) ||
        (direction === "down" && dy > 4);
      if (!valid) return null;
      const primary = horizontalMove ? Math.abs(dx) : Math.abs(dy);
      const secondary = horizontalMove ? Math.abs(dy) : Math.abs(dx);
      const sourceStart = horizontalMove ? source.top : source.left;
      const sourceEnd = horizontalMove ? source.bottom : source.right;
      const targetStart = horizontalMove ? rect.top : rect.left;
      const targetEnd = horizontalMove ? rect.bottom : rect.right;
      const bandGap = Math.max(0, targetStart - sourceEnd, sourceStart - targetEnd);
      const aligned = bandGap === 0;
      const angle = secondary / Math.max(primary, 1);
      const score = primary + bandGap * 5 + secondary * 0.22 + (aligned ? 0 : primary * 0.45);
      return { element, score, aligned, angle };
    })
    .filter(Boolean);

  const friendlyCandidates = candidates.filter((candidate) => candidate.aligned || candidate.angle <= 1.15);
  const ranked = (friendlyCandidates.length ? friendlyCandidates : candidates)
    .sort((a, b) => a.score - b.score);
  if (!ranked[0] && direction === "up" && activeView === "home" && app.classList.contains("is-media-browsing")) {
    setHomeBrowsing(false, true);
    return;
  }
  focusElement(ranked[0]?.element || current);
}

function setBackdrop(url) {
  const resolvedUrl = url || serverDefaultBackdrop;
  if (!resolvedUrl) {
    clearTimeout(backdropTimer);
    ambientImage.style.backgroundImage = "none";
    ambientImage.classList.remove("is-changing");
    return;
  }
  if (ambientImage.style.backgroundImage.includes(resolvedUrl)) return;
  clearTimeout(backdropTimer);
  ambientImage.classList.add("is-changing");
  backdropTimer = setTimeout(() => {
    ambientImage.style.backgroundImage = `url("${resolvedUrl}")`;
    ambientImage.classList.remove("is-changing");
  }, 170);
}

function normalizeServerAddress(address) {
  const trimmed = address.trim().replace(/\/+$/, "");
  if (!trimmed) return "";
  return /^[a-z][a-z\d+.-]*:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
}

function useServerDefaultBackdrop(address) {
  const serverUrl = normalizeServerAddress(address);
  serverDefaultBackdrop = serverUrl ? `${serverUrl}${JELLYFIN_SPLASHSCREEN_PATH}` : "";
  const cssImage = serverDefaultBackdrop ? `url("${serverDefaultBackdrop}")` : "none";
  document.documentElement.style.setProperty("--server-default-background", cssImage);

  mediaCatalog[0].image = serverDefaultBackdrop;
  mediaCatalog[0].backdrop = serverDefaultBackdrop;
  if (currentMedia.title === mediaCatalog[0].title) {
    currentMedia.image = serverDefaultBackdrop;
    currentMedia.backdrop = serverDefaultBackdrop;
  }

  document.querySelectorAll("[data-server-default-artwork]").forEach((image) => {
    image.hidden = !serverDefaultBackdrop;
    if (serverDefaultBackdrop) image.src = serverDefaultBackdrop;
    image.onerror = () => {
      image.hidden = true;
      document.documentElement.style.setProperty("--server-default-background", "none");
      if (!ambientImage.style.backgroundImage || ambientImage.style.backgroundImage.includes(serverDefaultBackdrop)) {
        ambientImage.style.backgroundImage = "none";
      }
    };
  });

  document.querySelectorAll("[data-server-default-media]").forEach((element) => {
    element.dataset.backdrop = serverDefaultBackdrop;
    element.dataset.image = serverDefaultBackdrop;
  });
  setBackdrop(serverDefaultBackdrop);
}

function inferMediaKind(meta = "") {
  const value = meta.toLowerCase();
  if (value.includes("photo album")) return "Photo album";
  if (value.includes("audiobook")) return "Audiobook";
  if (value.includes("playlist")) return "Playlist";
  if (value.includes("album")) return "Music album";
  if (value.includes("artist")) return "Music artist";
  if (value.includes("live now")) return "Live TV";
  if (value.includes("recording")) return "Recording";
  if (value.includes("episodes")) return "Series";
  return "Film";
}

function mediaMode(kind) {
  if (kind === "Photo album") return "photo";
  if (["Audiobook", "Playlist", "Music album", "Music artist"].includes(kind)) return "audio";
  return "video";
}

function primaryActionLabel(kind) {
  if (kind === "Photo album") return "Open album";
  if (kind === "Live TV") return "Watch live";
  if (kind === "Audiobook") return "Resume";
  if (kind === "Music artist") return "Play artist";
  if (kind === "Series") return "Resume";
  return "Play";
}

function seriesDetailsFor(media) {
  if (media.title === "Night Service") {
    return {
      seasons: nightServiceSeasons,
      facts: [
        ["Created by", "Aya Mercer"],
        ["Status", "Returning series"],
        ["Seasons", "1"],
        ["Episodes", "8"],
        ["Genres", "Mystery, Drama"],
        ["Rating", "TV-14"],
      ],
    };
  }

  const episodeCount = Math.max(4, Number.parseInt(media.meta.match(/(\d+)\s+episodes/i)?.[1] || "8", 10));
  const episodeTitles = [
    "Arrival",
    "The Long Way Home",
    "Open Doors",
    "After Dark",
    "The Crossing",
    "Second Light",
    "What Remains",
    "Home Again",
    "The Quiet Hour",
    "No Fixed Address",
    "A Small Distance",
    "The Last Morning",
  ];
  const slug = media.title.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const episodes = Array.from({ length: episodeCount }, (_, index) => ({
    number: `S1 E${index + 1}`,
    title: episodeTitles[index % episodeTitles.length],
    duration: `${44 + (index % 8)} min`,
    date: `${7 + index} Mar ${media.meta.split("|")[0] || "2025"}`,
    summary: `${media.title} continues with a new chapter.`,
    image: `https://picsum.photos/seed/${slug}-episode-${index + 1}/960/540`,
    progress: index < 2 ? 100 : index === 2 ? 34 : 0,
  }));

  return {
    seasons: [
      { id: "season-1", label: "Season 1", episodes },
      {
        id: "specials",
        label: "Specials",
        episodes: [{
          number: "S0 E1",
          title: "Making the series",
          duration: "22 min",
          date: media.meta.split("|")[0] || "2025",
          summary: `Behind the scenes of ${media.title}.`,
          image: `https://picsum.photos/seed/${slug}-special/960/540`,
          progress: 0,
        }],
      },
    ],
    facts: [
      ["Created by", "Mira Holt"],
      ["Status", "Returning series"],
      ["Seasons", "1"],
      ["Episodes", String(episodeCount)],
      ["Genres", media.meta.split("|").at(-1) || "Drama"],
      ["Rating", "TV-14"],
    ],
  };
}

function enrichEpisodeMetadata(episode, episodeIndex, seasonIndex) {
  const directors = ["Linh Aoki", "Sora Bennett", "Minh Trần", "Aya Mercer"];
  const writers = ["Aya Mercer", "Jonas Vale", "Nia Okafor", "Mara Levin"];
  return {
    rating: "TV-14",
    quality: episodeIndex % 3 === 0 ? "4K HDR" : "HD",
    audio: episodeIndex % 2 === 0 ? "English 5.1" : "English stereo",
    director: directors[(episodeIndex + seasonIndex) % directors.length],
    writer: writers[(episodeIndex + seasonIndex * 2) % writers.length],
    ...episode,
  };
}

function renderSeasonEpisodes() {
  if (!activeSeriesData) return;
  const seasonIndex = Math.max(
    0,
    activeSeriesData.seasons.findIndex((season) => season.id === detailSeasonSelect.value),
  );
  const season = activeSeriesData.seasons[seasonIndex];
  const cards = season.episodes.map((episode, episodeIndex) => {
    const card = document.createElement("button");
    card.className = "episode-card";
    card.dataset.focus = "";
    card.dataset.episodePlay = "";
    card.dataset.seasonIndex = String(seasonIndex);
    card.dataset.episodeIndex = String(episodeIndex);
    card.setAttribute("aria-label", `${episode.number}, ${episode.title}`);

    const artwork = document.createElement("span");
    artwork.className = "episode-card__art";
    const image = document.createElement("img");
    image.src = episode.image;
    image.alt = "";
    artwork.append(image);

    const play = document.createElement("span");
    play.className = "episode-card__play material-symbols-rounded";
    play.setAttribute("aria-hidden", "true");
    play.textContent = episode.progress === 100 ? "replay" : "play_arrow";
    play.classList.toggle("episode-card__play--replay", episode.progress === 100);
    artwork.append(play);

    if (episode.progress === 100) {
      const watched = document.createElement("span");
      watched.className = "episode-card__watched material-symbols-rounded";
      watched.setAttribute("aria-hidden", "true");
      watched.textContent = "check";
      artwork.append(watched);
    } else if (episode.progress > 0) {
      const progress = document.createElement("span");
      progress.className = "series-progress";
      const fill = document.createElement("i");
      fill.style.width = `${episode.progress}%`;
      progress.append(fill);
      artwork.append(progress);
    }

    const number = document.createElement("span");
    number.className = "episode-card__number";
    number.textContent = `${episode.number} · ${episode.duration} · ${episode.quality}`;
    const title = document.createElement("strong");
    title.textContent = episode.title;
    const summary = document.createElement("p");
    summary.className = "episode-card__summary";
    summary.textContent = episode.summary;
    const date = document.createElement("small");
    date.textContent = `${episode.date} · ${episode.rating}`;
    card.append(artwork, number, title, summary, date);
    return card;
  });
  detailEpisodeRow.replaceChildren(...cards);
}

function renderSeriesDetails() {
  activeSeriesData = seriesDetailsFor(currentMedia);
  activeSeriesData.seasons = activeSeriesData.seasons.map((season, seasonIndex) => ({
    ...season,
    episodes: season.episodes.map((episode, episodeIndex) =>
      enrichEpisodeMetadata(episode, episodeIndex, seasonIndex)),
  }));
  detailSeasonSelect.replaceChildren(...activeSeriesData.seasons.map((season) => {
    const option = document.createElement("option");
    option.value = season.id;
    option.textContent = season.label;
    return option;
  }));
  detailSeasonSelect.value = activeSeriesData.seasons[0].id;

  const allEpisodes = activeSeriesData.seasons.flatMap((season) => season.episodes);
  currentEpisode =
    allEpisodes.find((episode) => episode.progress > 0 && episode.progress < 100) ||
    allEpisodes.find((episode) => episode.progress === 0) ||
    allEpisodes[0];

  document.querySelector("#series-next-image").src = currentEpisode.image;
  document.querySelector("#series-next-number").textContent = currentEpisode.number;
  document.querySelector("#series-next-title").textContent = currentEpisode.title;
  document.querySelector("#series-next-summary").textContent = currentEpisode.summary;
  document.querySelector("#series-next-duration").textContent = currentEpisode.duration;
  document.querySelector("#series-next-date").textContent = currentEpisode.date;
  document.querySelector("#series-next-rating").textContent = currentEpisode.rating;
  document.querySelector("#series-next-quality").textContent = currentEpisode.quality;
  document.querySelector("#series-next-credits").textContent =
    `Directed by ${currentEpisode.director} · Written by ${currentEpisode.writer}`;
  document.querySelector("#series-next-progress").style.width = `${currentEpisode.progress}%`;
  document.querySelector("#detail-audio-select").options[0].textContent = currentEpisode.audio;
  document.querySelector("#detail-quality-select").options[0].textContent = `Original · ${currentEpisode.quality}`;
  document.querySelector("#detail-primary-label").textContent =
    currentEpisode.progress > 0 ? `Resume ${currentEpisode.number}` : `Play ${currentEpisode.number}`;
  renderSeasonEpisodes();
}

function renderDetailRelated() {
  const isSeries = currentMedia.kind === "Series";
  const mode = mediaMode(currentMedia.kind);
  const related = mediaCatalog
    .filter((item) => item.title !== currentMedia.title)
    .filter((item) => {
      if (isSeries) return item.type === "Series";
      if (currentMedia.kind === "Music artist") return inferMediaKind(item.meta) === "Music artist";
      if (currentMedia.kind === "Playlist") return inferMediaKind(item.meta) === "Playlist";
      if (mode === "audio") return mediaMode(inferMediaKind(item.meta)) === "audio";
      if (mode === "photo") return inferMediaKind(item.meta) === "Photo album";
      return item.type === "Film";
    })
    .slice(0, 5);

  const shape = mode === "audio" ? "square" : mode === "photo" ? "landscape" : "poster";
  detailRelatedRow.className = `media-row media-row--${shape}`;
  const cards = related.map((item) => {
    const card = document.createElement("button");
    card.className = `media-card media-card--${shape}`;
    card.dataset.focus = "";
    card.dataset.media = "";
    card.dataset.title = item.title;
    card.dataset.meta = item.meta;
    card.dataset.summary = item.summary;
    card.dataset.backdrop = item.backdrop;
    card.dataset.image = item.image;
    card.dataset.kind = inferMediaKind(item.meta);
    card.dataset.logo = item.hasLogo === false ? "none" : "wordmark";

    const image = document.createElement("img");
    image.src = item.image;
    image.alt = `${item.title} artwork`;
    const label = document.createElement("span");
    label.className = "media-card__label";
    const title = document.createElement("strong");
    title.textContent = item.title;
    const detail = document.createElement("small");
    detail.textContent = item.meta.split("|").at(-1);
    label.append(title, detail);
    card.append(image, label);
    return card;
  });
  detailRelatedRow.replaceChildren(...cards);
}

function createAudioTrackRow({
  title,
  artist,
  subtitle = artist,
  duration,
  bitDepth = "24-bit",
  sampleRate = "96 kHz",
  channels = "Stereo",
  lyrics = "",
  album = "",
  image = "",
}, index) {
  const button = document.createElement("button");
  button.className = "track-row";
  button.dataset.focus = "";
  button.dataset.audioTrack = "";
  button.dataset.trackTitle = title;
  button.dataset.trackArtist = artist;
  button.dataset.trackDuration = duration;
  button.dataset.trackFormat = "FLAC";
  button.dataset.trackBitDepth = bitDepth;
  button.dataset.trackSampleRate = sampleRate;
  button.dataset.trackChannels = channels;
  button.dataset.trackLyrics = lyrics;
  button.dataset.trackAlbum = album;
  button.dataset.trackImage = image;
  button.innerHTML = `
    <span class="track-row__index">
      <span>${index + 1}</span>
      <span class="material-symbols-rounded">play_arrow</span>
    </span>
    <span class="track-row__copy">
      <strong>${title}</strong>
      <small>${subtitle}</small>
      <span class="track-row__technical">
        <span>FLAC</span>
        <span>${bitDepth}</span>
        <span>${sampleRate}</span>
        <span>${channels}</span>
        ${lyrics ? `<span>${lyrics}</span>` : ""}
      </span>
    </span>
    <span class="track-row__format">FLAC</span>
    <time>${duration}</time>
    <span class="track-row__chevron material-symbols-rounded" aria-hidden="true">chevron_right</span>
  `;
  return button;
}

function renderMusicDetails() {
  const artist = currentMedia.artist || selectedMusicItem?.dataset.artist || "Unknown artist";
  const tracks = [
    ["First Light", "4:12", "24-bit", "96 kHz", "Stereo", "Synced lyrics"],
    ["Slow Meridian", "5:06", "24-bit", "96 kHz", "Stereo", "Lyrics"],
    ["Blue Hours", "4:48", "24-bit", "96 kHz", "Stereo", "Synced lyrics"],
    ["Open Water", "5:31", "24-bit", "48 kHz", "Stereo", ""],
    ["Still Moving", "4:27", "24-bit", "96 kHz", "Stereo", "Lyrics"],
    ["After the Signal", "6:02", "24-bit", "96 kHz", "Stereo", "Synced lyrics"],
    ["North Window", "5:14", "24-bit", "48 kHz", "Stereo", ""],
    ["Last Train Home", "6:40", "24-bit", "96 kHz", "Stereo", "Lyrics"],
  ].map(([title, duration, bitDepth, sampleRate, channels, lyrics], index) => ({
    title,
    artist: index === 2 ? `${artist} feat. Jun Vale` : artist,
    duration,
    bitDepth,
    sampleRate,
    channels,
    lyrics,
  }));
  document.querySelector("#detail-music-count").textContent = `${tracks.length} tracks · 42 min · 1 disc`;
  detailTrackList.replaceChildren(...tracks.map(createAudioTrackRow));
}

function renderPlaylistDetails() {
  const trackNames = [
    "First Light",
    "Soft Current",
    "Signal Bloom",
    "Low Tide",
    "Glass Garden",
    "Open Water",
    "Quiet Relay",
    "Northern Line",
    "After Image",
    "Paper Coast",
    "Static Bloom",
    "Night Window",
    "Plain Song",
    "Drift Map",
    "Slow Meridian",
    "Weathering",
    "Between Stations",
    "Warm Circuit",
    "Field Notes",
    "Last Train Home",
    "Thin Horizon",
    "Southbound",
    "Early Current",
    "Blue Hours",
    "Still Moving",
    "Salt Air",
    "Parallel Lines",
    "Morning Receiver",
  ];
  const artists = ["Marin Vale", "Jun Vale", "Glass Index", "Weather Systems", "Northern Static", "Mara Field"];
  const albums = ["Blue Hours", "Soft Current", "Nocturne Transit", "Field Recordings", "Still Forms", "Low Signal"];
  const durations = ["4:12", "3:48", "5:06", "4:34", "3:57", "5:31", "4:09"];
  const tracks = trackNames.map((title, index) => {
    const artist = artists[index % artists.length];
    const album = albums[index % albums.length];
    return {
      title,
      artist,
      album,
      subtitle: `${artist} · ${album}`,
      duration: durations[index % durations.length],
      bitDepth: index % 5 === 0 ? "16-bit" : "24-bit",
      sampleRate: index % 4 === 0 ? "48 kHz" : "96 kHz",
      lyrics: index % 3 === 0 ? "Synced lyrics" : "",
      image: `https://picsum.photos/seed/${album.toLowerCase().replaceAll(" ", "-")}/900/900`,
    };
  });
  document.querySelector("#detail-music-count").textContent = "28 tracks · 1 hr 58 min";
  detailTrackList.replaceChildren(...tracks.map(createAudioTrackRow));
}

function renderArtistDetails() {
  const artist = currentMedia.title;
  const tracks = [
    { title: "First Light", album: "Blue Hours", duration: "4:12", lyrics: "Synced lyrics", seed: "blue-hours-album" },
    { title: "Slow Meridian", album: "Blue Hours", duration: "5:06", lyrics: "Lyrics", seed: "blue-hours-album" },
    { title: "Signal Bloom", album: "Still Horizons", duration: "4:34", sampleRate: "48 kHz", seed: "still-horizons" },
    { title: "Low Tide", album: "Live at Northline", duration: "6:18", lyrics: "Synced lyrics", seed: "live-northline" },
    { title: "September Room", album: "Paper Coast", duration: "3:57", bitDepth: "16-bit", sampleRate: "44.1 kHz", seed: "paper-coast" },
  ].map((track) => ({
    ...track,
    artist,
    subtitle: track.album,
    image: `https://picsum.photos/seed/${track.seed}/900/900`,
  }));
  detailArtistTrackList.replaceChildren(...tracks.map(createAudioTrackRow));
  document.querySelector("#detail-artist-count").textContent = `${tracks.length} tracks · 27 min`;

  const releases = [
    ["Blue Hours", "2026 · Album", "blue-hours-album"],
    ["Still Horizons", "2024 · Album", "still-horizons"],
    ["Paper Coast", "2022 · Album", "paper-coast"],
    ["Live at Northline", "2025 · Live album", "live-northline"],
    ["Night Window", "2023 · EP", "night-window"],
    ["Early Current", "2020 · Album", "early-current"],
  ];
  detailArtistReleaseRow.replaceChildren(...releases.map(([title, meta, seed]) => {
    const button = document.createElement("button");
    button.className = "music-item";
    button.dataset.focus = "";
    button.dataset.media = "";
    button.dataset.title = title;
    button.dataset.artist = artist;
    button.dataset.meta = `Album|${meta.replace(" · ", "|")}|Lossless`;
    button.dataset.summary = `${title} by ${artist}.`;
    button.dataset.image = `https://picsum.photos/seed/${seed}/720/720`;
    button.dataset.backdrop = `https://picsum.photos/seed/${seed}/1600/1000`;
    button.innerHTML = `
      <img src="${button.dataset.image}" alt="${title} album artwork placeholder" />
      <strong>${title}</strong>
      <small>${meta}</small>
    `;
    return button;
  }));
}

function openTrackActions(track) {
  trackActionReturnFocus = track;
  trackActionContainer = track.closest(".detail-music, .detail-artist");
  trackActionsTitle.textContent = track.dataset.trackTitle;
  trackActions.hidden = false;
  trackActionContainer?.classList.add("is-action-mode");
  trackActions.querySelector('[data-track-action="favorite"]')
    .classList.toggle("is-selected", track.dataset.trackFavorite === "true");
  focusElement(trackActions.querySelector("[data-track-action]"));
}

function closeTrackActions(restore = true) {
  if (trackActions.hidden) return false;
  trackActions.hidden = true;
  trackActionContainer?.classList.remove("is-action-mode");
  if (restore) focusElement(trackActionReturnFocus);
  trackActionReturnFocus = null;
  trackActionContainer = null;
  return true;
}

function renderDetailAbout(facts) {
  const entries = facts || [
    ["Director", "Inés Salvat"],
    ["Genres", currentMedia.meta.split("|").at(-1) || "Drama"],
    ["Studio", "Northline Pictures"],
    ["Release", currentMedia.meta.split("|")[0] || "2026"],
    ["Rating", "TV-14"],
    ["Media", currentMedia.meta.includes("4K") ? "4K HDR, HEVC, 24 fps" : "HD, HEVC, 24 fps"],
  ];
  detailAboutGrid.replaceChildren(...entries.map(([label, value]) => {
    const item = document.createElement("div");
    const term = document.createElement("span");
    term.textContent = label;
    const description = document.createElement("strong");
    description.textContent = value;
    item.append(term, description);
    return item;
  }));
}

function setCurrentMedia(element) {
  if (!element?.dataset?.media && !element?.dataset?.title) return;
  currentMedia = {
    title: element.dataset.title,
    meta: element.dataset.meta,
    summary: element.dataset.summary,
    backdrop: element.dataset.backdrop,
    image: element.dataset.image || element.querySelector("img")?.src || element.dataset.backdrop,
    artist: element.dataset.artist,
    kind: element.dataset.kind || inferMediaKind(element.dataset.meta),
    hasLogo: element.dataset.logo !== "none",
  };
  setBackdrop(currentMedia.backdrop);
  if (activeView === "home") {
    heroTitle.textContent = currentMedia.title;
    heroTitle.hidden = !currentMedia.hasLogo;
    heroWordmark.classList.toggle("is-title-fallback", !currentMedia.hasLogo);
    if (currentMedia.hasLogo) {
      const words = currentMedia.title.split(" ");
      const lastWord = words.pop();
      heroWordmark.innerHTML = `<span>${words.join(" ") || currentMedia.title}</span><strong>${words.length ? lastWord : ""}</strong>`;
    } else {
      heroWordmark.innerHTML = `<strong>${currentMedia.title}</strong>`;
    }
    heroWordmark.setAttribute("aria-label", currentMedia.title);
    heroSummary.textContent = currentMedia.summary;
    document.querySelector("#hero-primary-label").textContent = primaryActionLabel(currentMedia.kind);
    heroMeta.replaceChildren(...currentMedia.meta.split("|").map((text) => {
      const span = document.createElement("span");
      span.textContent = text;
      return span;
    }));
  }
}

function showView(name) {
  closeOverlays(false);
  const previousView = activeView;
  const onboardingViews = ["connect", "connection-error", "profiles", "login", "recovery"];
  const compactStateViews = ["splash", "session-expired", "account-locked", "certificate-warning", "version-warning"];
  const setupViews = [...onboardingViews, ...compactStateViews];
  const useOnboardingTransition =
    onboardingViews.includes(previousView) &&
    onboardingViews.includes(name) &&
    typeof document.startViewTransition === "function";

  const commitView = () => {
    activeView = name;
    app.classList.toggle("app--onboarding", setupViews.includes(name));
    app.classList.toggle("is-searching", name === "search");
    app.classList.remove("is-media-browsing");
    document.querySelectorAll("[data-view]").forEach((view) => {
      const active = view.dataset.view === name;
      view.hidden = !active;
      view.classList.toggle("is-active", active);
    });
    const topNavItems = [...document.querySelectorAll(".top-nav__item")];
    topNavItems.forEach((item) => item.classList.remove("is-active"));
    const activeTopItem =
      name === "library"
        ? topNavItems.find((item) => item.dataset.library === activeLibraryId)
        : topNavItems.find((item) => item.dataset.nav === name);
    activeTopItem?.classList.add("is-active");
    document.querySelector(".topbar-settings")?.classList.toggle("is-active", name === "settings");
    const contexts = {
      connect: "",
      "connection-error": "",
      profiles: "",
      login: "",
      recovery: "",
      splash: "",
      "session-expired": "",
      "account-locked": "",
      "certificate-warning": "",
      "version-warning": "",
      home: "Good evening",
      search: "Search",
      library: "Your library",
      music: "Music",
      live: "Live TV",
      settings: "Settings",
    };
    document.querySelector("#topbar-context").textContent = contexts[name] || "";
    content.scrollTop = 0;
    setBackdrop(name === "home" ? currentMedia.backdrop : serverDefaultBackdrop);
    if (name === "connection-error") rotateConnectionFailureMessage();
    if (name === "home" || name === "library") rotateFeedEndMessage(name);
    if (name === "home") document.querySelector('[data-view="home"]').classList.remove("is-browsing");
    if (name === "live") document.querySelector('[data-view="live"]').classList.remove("is-guide-focused");
    if (name === "music") {
      document.querySelector('[data-view="music"]').classList.remove("is-browsing");
      const firstMusicItem = document.querySelector('[data-view="music"] [data-music-item]');
      setCurrentMedia(firstMusicItem);
      selectMusicItem(firstMusicItem);
    }
  };

  if (useOnboardingTransition) {
    document.documentElement.dataset.onboardingDirection =
      onboardingViews.indexOf(name) > onboardingViews.indexOf(previousView) ? "forward" : "backward";
    const transition = document.startViewTransition(commitView);
    transition.finished.finally(() => {
      delete document.documentElement.dataset.onboardingDirection;
    });
  } else {
    commitView();
  }

  setTimeout(() => {
    const view = document.querySelector(`[data-view="${name}"]`);
    const remembered = focusMemory.get(name);
    const first =
      (remembered && view?.contains(remembered) ? remembered : null) ||
      view?.querySelector("[data-default-focus]") ||
      view?.querySelector("[data-focus]");
    focusElement(first);
    if (name === "home") content.scrollTop = 0;
  }, useOnboardingTransition ? 510 : 30);
}

function renderPersonTitleCard(item, index) {
  const button = document.createElement("button");
  button.className = "media-card media-card--poster";
  button.dataset.focus = "";
  button.dataset.personMediaIndex = String(index);
  button.setAttribute("aria-label", `${item.title}, ${item.type}`);
  const image = document.createElement("img");
  image.src = item.image;
  image.alt = `${item.title} artwork`;
  const label = document.createElement("span");
  label.className = "media-card__label";
  const title = document.createElement("strong");
  title.textContent = item.title;
  const detail = document.createElement("small");
  detail.textContent = `${item.year}, ${item.type}`;
  label.append(title, detail);
  button.append(image, label);
  return button;
}

function renderPersonDetails(name, role) {
  const videoTitles = mediaCatalog
    .filter((item) => item.type === "Film" || item.type === "Series")
    .slice(0, 8);
  personCredits = videoTitles;
  personKnownRow.replaceChildren(
    ...videoTitles.slice(0, 5).map(renderPersonTitleCard),
  );
  personCreditsList.replaceChildren(...videoTitles.map((item, index) => {
    const button = document.createElement("button");
    button.className = "person-credit";
    button.dataset.focus = "";
    button.dataset.personCredit = String(index);
    button.setAttribute("aria-label", `${item.title}, ${item.year}`);
    const credit = role === "Director"
      ? "Director"
      : ["Mara", "Nadia Vale", "Elena", "June", "Dr. Venn", "Leah", "Mina", "Sora"][index];
    button.innerHTML = `
      <span>${item.year}</span>
      <strong>${item.title}</strong>
      <small>${credit}</small>
      <span class="material-symbols-rounded" aria-hidden="true">chevron_right</span>
    `;
    return button;
  }));
  document.querySelector("#person-credit-count").textContent = `${videoTitles.length} titles`;
}

function openPerson(card) {
  personReturnFocus = card;
  const name = card.querySelector("strong")?.textContent || "Cast member";
  const credit = card.querySelector("small")?.textContent || "Actor";
  const image = card.querySelector("img")?.src || "https://picsum.photos/seed/cast-person/720/900";
  const role = credit === "Director" ? "Director" : "Actor";
  const biographies = {
    "Sora Venn": "Known for quiet, character-led dramas and contemporary mysteries.",
    "Idris Adebayo": "Known for ensemble dramas, thrillers, and independent film.",
    "Lena KovÃ¡cs": "Known for international drama and atmospheric mystery series.",
    "Minh Tráº§n": "Known for dramatic television and contemporary cinema.",
    "InÃ©s Salvat": "A director known for restrained dramas and location-led storytelling.",
  };
  document.querySelector("#person-name").textContent = name;
  document.querySelector("#person-role").textContent = role;
  document.querySelector("#person-biography").textContent =
    biographies[name] || `Known for work across drama, mystery, and contemporary film.`;
  document.querySelector("#person-portrait").src = image;
  document.querySelector("#person-portrait").alt = `${name} portrait`;
  document.querySelector("#person-backdrop").style.backgroundImage = `url("${image}")`;
  const favorite = personLayer.querySelector("[data-person-favorite]");
  favorite.setAttribute("aria-label", `Favorite ${name}`);
  renderPersonDetails(name, role);
  personLayer.hidden = false;
  personLayer.scrollTop = 0;
  focusElement(personLayer.querySelector("[data-close-person]"));
}

function closePerson(restore = true) {
  if (personLayer.hidden) return false;
  personLayer.hidden = true;
  if (restore) focusElement(personReturnFocus);
  return true;
}

function openPersonTitle(index) {
  const item = personCredits[index];
  if (!item) return;
  const returnFocus = personReturnFocus;
  closePerson(false);
  currentMedia = { ...item, kind: item.type };
  openDetails();
  previousFocus = returnFocus;
}

function openDetails() {
  previousFocus = document.activeElement;
  currentEpisode = null;
  currentAudioTrack = null;
  closeTrackActions(false);
  activeSeriesData = null;
  document.querySelector("#detail-title").textContent = currentMedia.title;
  document.querySelector("#detail-summary").textContent = currentMedia.summary;
  document.querySelector("#detail-meta").replaceChildren(...[currentMedia.kind, ...currentMedia.meta.split("|")].map((text) => {
    const span = document.createElement("span");
    span.textContent = text;
    return span;
  }));
  document.querySelector("#detail-backdrop").style.backgroundImage = `url("${currentMedia.backdrop}")`;
  document.querySelector("#detail-primary-label").textContent = primaryActionLabel(currentMedia.kind);
  const words = currentMedia.title.split(" ");
  const lastWord = words.pop();
  detailWordmark.innerHTML = `<span>${words.join(" ") || currentMedia.title}</span><strong>${words.length ? lastWord : ""}</strong>`;
  detailWordmark.setAttribute("aria-label", currentMedia.title);
  const factSets = {
    audio: [["Format", "FLAC · 24-bit"], ["Quality", "96 kHz · Stereo"], ["Lyrics", "Synchronized"]],
    photo: [["Contents", currentMedia.meta.split("|")[1] || "Photo library"], ["Type", "Photo album"], ["Display", "Original quality"]],
  };
  const mode = mediaMode(currentMedia.kind);
  const isSeries = currentMedia.kind === "Series";
  const isArtistDetail = currentMedia.kind === "Music artist";
  const isAlbumDetail = currentMedia.kind === "Music album";
  const isPlaylistDetail = currentMedia.kind === "Playlist";
  const hasMusicTrackList = isAlbumDetail || isPlaylistDetail;
  const isMusicDetail = hasMusicTrackList || isArtistDetail;
  detailLayer.dataset.mode =
    isArtistDetail ? "artist" : isPlaylistDetail ? "playlist" : isAlbumDetail ? "music" : mode;
  detailSelects.hidden = mode !== "video";
  detailStaticFacts.hidden = mode === "video";
  detailSeries.hidden = !isSeries;
  detailMusic.hidden = !hasMusicTrackList;
  detailArtist.hidden = !isArtistDetail;
  detailWordmark.hidden = isMusicDetail;
  detailCover.hidden = !isMusicDetail;
  document.querySelector("#detail-cast-shelf").hidden = mode !== "video";
  document.querySelector("#related-title").textContent =
    isArtistDetail
      ? "Similar artists"
      : isPlaylistDetail
        ? "More playlists"
      : isAlbumDetail
        ? `More from ${currentMedia.artist || "this artist"}`
        : "More like this";
  document.querySelector("#about-title").textContent =
    isArtistDetail
      ? `About ${currentMedia.title}`
      : isPlaylistDetail
        ? "About this playlist"
        : isAlbumDetail
          ? "About this album"
          : "About this title";
  if (isAlbumDetail) {
    detailMusicBody.append(trackActions);
    detailCover.src = currentMedia.image || currentMedia.backdrop;
    detailCover.alt = `${currentMedia.title} album cover`;
    document.querySelector("#detail-meta").replaceChildren(...[
      currentMedia.artist || "Marin Vale",
      "Album",
      currentMedia.meta.split("|")[1] || "2026",
      "8 tracks",
      "42 min",
      "24-bit / 96 kHz",
    ].map((text) => {
      const span = document.createElement("span");
      span.textContent = text;
      return span;
    }));
    renderMusicDetails();
  }
  if (isPlaylistDetail) {
    detailMusicBody.append(trackActions);
    detailCover.src = currentMedia.image || currentMedia.backdrop;
    detailCover.alt = `${currentMedia.title} playlist artwork`;
    document.querySelector("#detail-meta").replaceChildren(...[
      "Playlist",
      "28 tracks",
      "1 hr 58 min",
      "Updated today",
    ].map((text) => {
      const span = document.createElement("span");
      span.textContent = text;
      return span;
    }));
    renderPlaylistDetails();
  }
  if (isArtistDetail) {
    detailArtistPopularBody.append(trackActions);
    detailCover.src = currentMedia.image || currentMedia.backdrop;
    detailCover.alt = `${currentMedia.title} artist portrait`;
    document.querySelector("#detail-meta").replaceChildren(...[
      "Artist",
      "6 albums",
      "58 tracks",
      "Lossless",
    ].map((text) => {
      const span = document.createElement("span");
      span.textContent = text;
      return span;
    }));
    renderArtistDetails();
  }
  if (isSeries) {
    renderSeriesDetails();
  } else if (mode === "video") {
    document.querySelector("#detail-audio-select").options[0].textContent = "English 5.1";
    document.querySelector("#detail-quality-select").options[0].textContent =
      `Original · ${currentMedia.meta.includes("4K") ? "4K HDR" : "HD"}`;
  }
  renderDetailRelated();
  renderDetailAbout(
    isSeries
      ? activeSeriesData.facts
      : isArtistDetail
        ? [
            ["Genres", "Ambient, electronic"],
            ["Albums", "6"],
            ["Tracks", "58"],
            ["Latest release", "Blue Hours · 2026"],
            ["Added", "12 May 2023"],
            ["Library", "Music"],
            ["Sort name", "Vale, Marin"],
            ["Identification", "MusicBrainz linked"],
            ["Audio", "Lossless available"],
          ]
      : isPlaylistDetail
        ? [
            ["Owner", "Maik"],
            ["Created", "18 June 2026"],
            ["Modified", "Today"],
            ["Tracks", "28"],
            ["Duration", "1 hr 58 min"],
            ["Library", "Music"],
            ["Playback", "Mixed quality"],
            ["Collaborative", "No"],
            ["Type", "Jellyfin playlist"],
          ]
      : isAlbumDetail
        ? [
            ["Artist", currentMedia.artist || "Marin Vale"],
            ["Release", "14 February 2026"],
            ["Label", "Northline Records"],
            ["Tracks", "8"],
            ["Discs", "1"],
            ["Duration", "42 min"],
            ["Audio", "FLAC · 24-bit / 96 kHz"],
            ["Added", "2 days ago"],
            ["Identification", "MusicBrainz linked"],
          ]
        : null,
  );
  if (mode !== "video") {
    const facts = isArtistDetail
      ? [["Albums", "6 releases"], ["Tracks", "58 songs"], ["Quality", "Lossless"]]
      : isPlaylistDetail
        ? [["Owner", "Maik"], ["Tracks", "28 songs"], ["Duration", "1 hr 58 min"]]
      : factSets[mode];
    ["one", "two", "three"].forEach((slot, index) => {
      document.querySelector(`#fact-label-${slot}`).textContent = facts[index][0];
      document.querySelector(`#fact-value-${slot}`).textContent = facts[index][1];
    });
  }
  detailLayer.hidden = false;
  detailLayer.scrollTop = 0;
  focusElement(detailLayer.querySelector("[data-close-detail]"));
}

function formatPlayerTime(seconds) {
  const value = Math.max(0, Math.round(seconds));
  const hours = Math.floor(value / 3600);
  const minutes = Math.floor((value % 3600) / 60);
  const remainder = value % 60;
  return hours
    ? `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`
    : `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

function playerDurationSeconds() {
  const episodeMinutes = Number.parseInt(currentEpisode?.duration || "", 10);
  return Number.isFinite(episodeMinutes) ? episodeMinutes * 60 : 6720;
}

function playerChapterAt(position) {
  const chapters = [
    { position: 0, label: "Opening" },
    { position: 18, label: "The platform" },
    { position: 46, label: "Rear carriage" },
    { position: 76, label: "Red signal" },
  ];
  return chapters.findLast((chapter) => position >= chapter.position) || chapters[0];
}

function updatePlayerTimeline(delta = 0) {
  playerPosition = Math.min(100, Math.max(0, playerPosition + delta));
  const totalSeconds = playerDurationSeconds();
  const elapsed = totalSeconds * (playerPosition / 100);
  const progress = `${playerPosition}%`;
  document.querySelector("#player-timeline-progress").style.width = progress;
  document.querySelector("#player-timeline-handle").style.left = progress;
  document.querySelector("#player-elapsed").textContent = formatPlayerTime(elapsed);
  document.querySelector("#player-remaining").textContent = `-${formatPlayerTime(totalSeconds - elapsed)}`;
  document.querySelector("#player-mini-progress").style.width = progress;
  document.querySelector("#player-mini-elapsed").textContent = formatPlayerTime(elapsed);
  document.querySelector("#player-mini-remaining").textContent = `-${formatPlayerTime(totalSeconds - elapsed)}`;
  document.querySelector("#player-timeline-preview-time").textContent = formatPlayerTime(elapsed);
  document.querySelector("#player-timeline-preview-chapter").textContent = playerChapterAt(playerPosition).label;
  playerTimelinePreview.style.left = `${Math.min(92, Math.max(8, playerPosition))}%`;
  playerTimelinePreviewFrame.style.backgroundPosition = `center ${34 + Math.round(playerPosition * 0.32)}%`;
  playerTimeline.querySelectorAll(".timeline__track em").forEach((marker) => {
    marker.classList.toggle("is-passed", Number.parseFloat(marker.style.left) <= playerPosition);
  });
  playerTimeline.setAttribute("aria-label", `Playback position, ${Math.round(playerPosition)} percent`);
}

function seekPlayerSeconds(seconds) {
  updatePlayerTimeline((seconds / playerDurationSeconds()) * 100);
  if (
    playerPosition >= 100 &&
    postPlay.hidden &&
    (currentMedia.kind === "Series" || currentMedia.kind === "Film")
  ) {
    openPostPlay();
  }
}

function showPlayerMiniSeek() {
  clearTimeout(playerMiniSeekTimer);
  playerMiniSeek.classList.add("is-visible");
  playerMiniSeek.setAttribute("aria-hidden", "false");
  playerMiniSeekTimer = setTimeout(() => {
    playerMiniSeek.classList.remove("is-visible");
    playerMiniSeek.setAttribute("aria-hidden", "true");
  }, 1100);
}

function setPlayerOsdVisible(visible, focusTarget = "controls") {
  playerLayer.classList.toggle("is-osd-hidden", !visible);
  if (visible) {
    playerMiniSeek.classList.remove("is-visible");
    playerMiniSeek.setAttribute("aria-hidden", "true");
    focusElement(
      focusTarget === "timeline"
        ? playerTimeline
        : playerLayer.querySelector(".player-control--primary"),
    );
  } else {
    document.querySelectorAll(".is-key-focused").forEach((node) => node.classList.remove("is-key-focused"));
    playerLayer.focus({ preventScroll: true });
  }
}

function renderPlayerWordmark() {
  const words = currentMedia.title.split(" ");
  const lastWord = words.pop();
  const hasLogo = currentMedia.hasLogo !== false;
  playerWordmark.classList.toggle("is-title-fallback", !hasLogo);
  if (hasLogo) {
    playerWordmark.innerHTML =
      `<span>${words.join(" ") || currentMedia.title}</span><strong>${words.length ? lastWord : ""}</strong>`;
  } else {
    playerWordmark.innerHTML = `<strong>${currentMedia.title}</strong>`;
  }
  playerWordmark.setAttribute("aria-label", currentMedia.title);
}

function playerDrawerConfig(type) {
  const setting = (title, icon, settingKey, values) => ({
    title,
    icon,
    settingKey,
    options: values.map(([label, detail]) => ({
      label,
      detail,
      selected: playbackOptions[settingKey] === label,
    })),
  });
  const configs = {
    subtitles: {
      title: "Subtitles",
      icon: "subtitles",
      options: [
        { label: "Off", detail: "No subtitles" },
        { label: "English", detail: "Full", selected: true },
        { label: "English SDH", detail: "Hearing impaired" },
        { label: "Vietnamese", detail: "Full" },
      ],
    },
    audio: {
      title: "Audio",
      icon: "graphic_eq",
      options: [
        { label: currentEpisode?.audio || "English 5.1", detail: "Dolby Digital Plus", selected: true },
        { label: "Japanese 5.1", detail: "Dolby Digital Plus" },
        { label: "Commentary", detail: "Stereo" },
      ],
    },
    chapters: {
      title: "Chapters",
      icon: "video_library",
      options: [
        { label: "Opening", detail: "00:00" },
        { label: "The platform", detail: "08:42" },
        { label: "Rear carriage", detail: "21:37", selected: true },
        { label: "Red signal", detail: "35:49" },
      ],
    },
    more: {
      title: "Playback options",
      icon: "tune",
      options: [
        { label: "Playback speed", detail: playbackOptions.speed, navigable: true, target: "playback-speed" },
        { label: "Frame", detail: playbackOptions.aspect, navigable: true, target: "playback-aspect" },
        { label: "HDR handling", detail: playbackOptions.hdr, navigable: true, target: "playback-hdr" },
        { label: "Video", detail: playbackOptions.videoTrack, navigable: true, target: "playback-video" },
        { label: "Audio delay", detail: playbackOptions.audioDelay, navigable: true, target: "playback-audio-delay" },
        { label: "Subtitle delay", detail: playbackOptions.subtitleDelay, navigable: true, target: "playback-subtitle-delay" },
        { label: "Deinterlace", detail: playbackOptions.deinterlace, navigable: true, target: "playback-deinterlace" },
        { label: "Sleep timer", detail: playbackOptions.sleepTimer, navigable: true, target: "playback-sleep" },
        { label: "Playback information", detail: "Direct play", navigable: true, target: "playback-information" },
      ],
    },
    "playback-speed": setting("Playback speed", "speed", "speed", [
      ["0.5×", "Half speed"],
      ["0.75×", "Slower"],
      ["Normal", "1×"],
      ["1.25×", "Faster"],
      ["1.5×", "Faster"],
      ["2×", "Double speed"],
    ]),
    "playback-aspect": setting("Frame", "aspect_ratio", "aspect", [
      ["Fit", "Show the full picture"],
      ["Fill", "Crop to the display"],
      ["Original", "Source dimensions"],
      ["16:9", "Widescreen"],
      ["4:3", "Classic"],
    ]),
    "playback-hdr": setting("HDR handling", "hdr_on", "hdr", [
      ["Auto", "Match the display"],
      ["Passthrough", "Send HDR unchanged"],
      ["Tone map", "Adapt to this display"],
      ["Convert to SDR", "Force standard range"],
    ]),
    "playback-video": setting("Video", "movie", "videoTrack", [
      ["HEVC · Main 10", "4K HDR · 38 Mbps"],
      ["H.264 · High", "1080p · 12 Mbps"],
    ]),
    "playback-audio-delay": {
      title: "Audio delay",
      icon: "graphic_eq",
      control: "delay",
      settingKey: "audioDelay",
    },
    "playback-subtitle-delay": {
      title: "Subtitle delay",
      icon: "subtitles",
      control: "delay",
      settingKey: "subtitleDelay",
    },
    "playback-deinterlace": setting("Deinterlace", "deinterlace", "deinterlace", [
      ["Auto", "Use source metadata"],
      ["On", "Always deinterlace"],
      ["Off", "Leave source unchanged"],
    ]),
    "playback-sleep": setting("Sleep timer", "bedtime", "sleepTimer", [
      ["Off", "Keep playing"],
      ["15 min", "Stop after 15 minutes"],
      ["30 min", "Stop after 30 minutes"],
      ["45 min", "Stop after 45 minutes"],
      ["1 hr", "Stop after one hour"],
      ["End of episode", "Stop before the next item"],
    ]),
    "playback-information": {
      title: "Playback information",
      icon: "info",
      options: [
        { label: "Source", detail: "Direct play", informational: true },
        { label: "Container", detail: "Matroska · 41.2 Mbps", informational: true },
        { label: "Video", detail: "HEVC Main 10 · 3840×2160 · 23.976 fps", informational: true },
        { label: "Color", detail: "BT.2020 · HDR10 · 10-bit", informational: true },
        { label: "Audio", detail: "Dolby Digital Plus · 5.1 · 640 kbps", informational: true },
        { label: "Display", detail: "3840×2160 · 60 Hz", informational: true },
        { label: "Decoder", detail: "mpv · hardware accelerated", informational: true },
        { label: "Dropped frames", detail: "0", informational: true },
      ],
    },
  };
  return configs[type] || configs.more;
}

function formatDelayValue(milliseconds) {
  if (milliseconds === 0) return "0 ms";
  const sign = milliseconds > 0 ? "+" : "-";
  const absolute = Math.abs(milliseconds);
  if (absolute < 1000) return `${sign}${absolute} ms`;
  return `${sign}${Number((absolute / 1000).toFixed(3))} s`;
}

function updateDelayControl(settingKey) {
  const state = playbackDelayState[settingKey];
  const control = playerDrawerOptions.querySelector(`[data-delay-slider="${settingKey}"]`);
  if (!state || !control) return;
  const span = Math.max(1, state.max - state.min);
  const valuePosition = ((state.value - state.min) / span) * 100;
  const zeroPosition = ((0 - state.min) / span) * 100;
  const fill = control.querySelector(".player-delay__fill");
  control.querySelector("[data-delay-value]").textContent = formatDelayValue(state.value);
  control.querySelector("[data-delay-min]").textContent = formatDelayValue(state.min);
  control.querySelector("[data-delay-max]").textContent = formatDelayValue(state.max);
  control.querySelector(".player-delay__handle").style.left = `${valuePosition}%`;
  control.querySelector(".player-delay__zero").style.left = `${zeroPosition}%`;
  fill.style.left = `${Math.min(valuePosition, zeroPosition)}%`;
  fill.style.width = `${Math.abs(valuePosition - zeroPosition)}%`;
  playerDrawerOptions.querySelector('[data-delay-bound="min"] strong').textContent = formatDelayValue(state.min);
  playerDrawerOptions.querySelector('[data-delay-bound="max"] strong').textContent = formatDelayValue(state.max);
  playbackOptions[settingKey] = formatDelayValue(state.value);
  control.setAttribute("aria-label", `${settingKey === "audioDelay" ? "Audio" : "Subtitle"} delay, ${playbackOptions[settingKey]}`);
}

function renderDelayControl(settingKey) {
  playerDrawerOptions.innerHTML = `
    <button class="player-delay" data-focus data-delay-slider="${settingKey}">
      <span class="player-delay__heading">
        <span>Adjustment</span>
        <strong data-delay-value>0 ms</strong>
      </span>
      <span class="player-delay__track" aria-hidden="true">
        <i class="player-delay__fill"></i>
        <em class="player-delay__zero"></em>
        <b class="player-delay__handle"></b>
      </span>
      <span class="player-delay__range" aria-hidden="true">
        <small data-delay-min>-5 s</small>
        <small data-delay-max>+5 s</small>
      </span>
    </button>
    <button class="player-delay-reset" data-focus data-delay-reset="${settingKey}">
      <span class="material-symbols-rounded">restart_alt</span><span>Reset</span>
    </button>
    <button class="player-delay-bound" data-focus data-delay-bound="min" data-delay-setting="${settingKey}">
      <span><span>Earlier limit</span><small>Left or right to adjust</small></span>
      <strong>-5 s</strong>
    </button>
    <button class="player-delay-bound" data-focus data-delay-bound="max" data-delay-setting="${settingKey}">
      <span><span>Later limit</span><small>Left or right to adjust</small></span>
      <strong>+5 s</strong>
    </button>
  `;
  updateDelayControl(settingKey);
}

function adjustDelayControl(element, direction) {
  const sliderKey = element.dataset.delaySlider;
  const settingKey = sliderKey || element.dataset.delaySetting;
  const state = playbackDelayState[settingKey];
  if (!state) return;
  const delta = direction === "left" ? -1 : 1;
  if (sliderKey) {
    state.value = Math.min(state.max, Math.max(state.min, state.value + delta * 125));
  } else if (element.dataset.delayBound === "min") {
    state.min = Math.min(-500, state.min + delta * 1000);
    state.value = Math.max(state.min, state.value);
  } else {
    state.max = Math.max(500, state.max + delta * 1000);
    state.value = Math.min(state.max, state.value);
  }
  updateDelayControl(settingKey);
}

function renderPlayerDrawer(type, preferredTarget = null) {
  const config = playerDrawerConfig(type);
  playerDrawerType = type;
  document.querySelector("#player-drawer-title").textContent = config.title;
  document.querySelector("#player-drawer-icon").textContent = config.icon;
  playerDrawerOptions.classList.toggle("is-delay-control", config.control === "delay");
  if (config.control === "delay") {
    renderDelayControl(config.settingKey);
    playerDrawer.hidden = false;
    focusElement(playerDrawerOptions.querySelector("[data-delay-slider]"));
    return;
  }
  const options = config.options.map((option) => {
    const button = document.createElement("button");
    button.className = "player-option";
    button.dataset.focus = "";
    button.dataset.playerOption = "";
    button.dataset.playerOptionType = type;
    button.dataset.playerOptionValue = option.label;
    if (option.target) button.dataset.playerOptionTarget = option.target;
    if (config.settingKey) button.dataset.playerSettingKey = config.settingKey;
    button.classList.toggle("is-selected", Boolean(option.selected));
    button.classList.toggle("is-navigable", Boolean(option.navigable));
    button.classList.toggle("is-informational", Boolean(option.informational));
    button.innerHTML = `
      <span><strong>${option.label}</strong><small>${option.detail}</small></span>
      <span class="material-symbols-rounded">${option.navigable ? "chevron_right" : option.selected ? "check" : ""}</span>
    `;
    return button;
  });
  playerDrawerOptions.replaceChildren(...options);
  playerDrawer.hidden = false;
  focusElement(
    (preferredTarget && playerDrawer.querySelector(`[data-player-option-target="${preferredTarget}"]`)) ||
    playerDrawer.querySelector(".player-option.is-selected") ||
    playerDrawer.querySelector(".player-option"),
  );
}

function openPlayerDrawer(type, returnFocus, fromOption = null) {
  if (returnFocus) {
    playerDrawerReturnFocus = returnFocus;
    playerDrawerHistory = [];
  }
  if (fromOption && playerDrawerType) {
    playerDrawerHistory.push({
      type: playerDrawerType,
      target: fromOption.dataset.playerOptionTarget,
    });
  }
  renderPlayerDrawer(type);
}

function backPlayerDrawer() {
  if (playerDrawer.hidden) return false;
  const previous = playerDrawerHistory.pop();
  if (previous) {
    renderPlayerDrawer(previous.type, previous.target);
    return true;
  }
  return closePlayerDrawer();
}

function closePlayerDrawer() {
  if (playerDrawer.hidden) return false;
  playerDrawer.hidden = true;
  playerDrawerType = null;
  playerDrawerHistory = [];
  focusElement(playerDrawerReturnFocus || playerLayer.querySelector(".player-control--primary"));
  return true;
}

const audioPlaybackQueue = [
  { title: "First Light", artist: "Marin Vale", album: "Blue Hours", duration: "4:12", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
  { title: "Slow Meridian", artist: "Marin Vale", album: "Blue Hours", duration: "5:06", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
  { title: "Blue Hours", artist: "Marin Vale feat. Jun Vale", album: "Blue Hours", duration: "4:48", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
  { title: "Open Water", artist: "Marin Vale", album: "Blue Hours", duration: "5:31", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "48 kHz", channels: "Stereo" },
  { title: "Still Moving", artist: "Marin Vale", album: "Blue Hours", duration: "4:27", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
  { title: "After the Signal", artist: "Marin Vale", album: "Blue Hours", duration: "6:02", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
  { title: "North Window", artist: "Marin Vale", album: "Blue Hours", duration: "5:14", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "48 kHz", channels: "Stereo" },
  { title: "Last Train Home", artist: "Marin Vale", album: "Blue Hours", duration: "6:40", seed: "blue-hours-album", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
].map((track) => ({
  ...track,
  image: `https://picsum.photos/seed/${track.seed}/900/900`,
}));

const audioSuggestedTracks = [
  { title: "Soft Current", artist: "Nora Field", album: "Still Water", duration: "4:03", seed: "still-water-nora", format: "FLAC", bitDepth: "24-bit", sampleRate: "48 kHz", channels: "Stereo" },
  { title: "Northbound", artist: "Glass Harbour", album: "Night Lines", duration: "3:46", seed: "night-lines-harbour", format: "AAC", bitDepth: "16-bit", sampleRate: "44.1 kHz", channels: "Stereo" },
  { title: "Low Sun", artist: "Jun Vale", album: "Open Country", duration: "5:18", seed: "open-country-jun", format: "FLAC", bitDepth: "24-bit", sampleRate: "96 kHz", channels: "Stereo" },
  { title: "After Rain", artist: "Mira North", album: "Window Weather", duration: "4:34", seed: "window-weather-mira", format: "FLAC", bitDepth: "24-bit", sampleRate: "48 kHz", channels: "Stereo" },
].map((track) => ({
  ...track,
  image: `https://picsum.photos/seed/${track.seed}/900/900`,
}));

const audioPlaybackLyrics = [
  ["00:18", 7, "First light through the station glass"],
  ["00:42", 17, "A quiet signal moving west"],
  ["01:09", 27, "We held the line until it vanished"],
  ["01:38", 38, "Then let the morning do the rest"],
  ["02:14", 53, "Open water, open sky"],
  ["02:48", 67, "No map between your hand and mine"],
  ["03:22", 81, "The northern window catches fire"],
  ["03:54", 94, "And every shadow falls behind"],
];

function audioContextTracks(type) {
  if (type === "suggested") return audioSuggestedTracks.slice(0, 4);
  const currentIndex = Math.max(0, audioPlaybackQueue.findIndex((track) => track.title === currentAudioTrack?.title));
  return [1, 2, 3, 4].map((offset) => audioPlaybackQueue[(currentIndex + offset) % audioPlaybackQueue.length]);
}

function renderAudioContextColumn(type) {
  const section = audioContextColumns.querySelector(`[data-audio-context-column="${type}"]`);
  const content = type === "suggested" ? audioSuggestedContent : audioUpNextContent;
  const toggle = section.querySelector("[data-audio-context-view]");
  const view = audioContextViews[type];
  const nextView = view === "covers" ? "tracks" : "covers";
  const tracks = audioContextTracks(type);
  section.dataset.presentation = view;
  toggle.querySelector(".material-symbols-rounded").textContent =
    nextView === "covers" ? "grid_view" : "view_agenda";
  toggle.querySelector("span:last-child").textContent =
    nextView === "covers" ? "Covers" : "Tracks";
  toggle.setAttribute(
    "aria-label",
    `Show ${type === "suggested" ? "Suggested" : "Up next"} as ${nextView === "covers" ? "album covers" : "tracks"}`,
  );

  content.replaceChildren(...tracks.map((track, index) => {
    const item = document.createElement("button");
    item.className = view === "covers" ? "audio-context-cover" : "audio-context-track";
    item.dataset.focus = "";
    item.dataset.audioContextSource = type;
    item.dataset.audioContextIndex = index;
    item.setAttribute("aria-label", `Play ${track.title} by ${track.artist}`);
    item.innerHTML = view === "covers"
      ? `
        <img src="${track.image}" alt="" />
        <span><strong>${track.title}</strong><small>${track.artist}</small></span>
      `
      : `
        <span>${String(index + 1).padStart(2, "0")}</span>
        <span><strong>${track.title}</strong><small>${track.artist}</small></span>
        <time>${track.duration}</time>
      `;
    return item;
  }));
}

function renderAudioPlayerContext(mode = null) {
  const isLyrics = mode === "lyrics";
  audioPlayerContextMode = isLyrics ? "lyrics" : null;
  audioPlayerContext.classList.toggle("is-active", isLyrics);
  audioPlayerContext.dataset.mode = isLyrics ? "lyrics" : "preview";
  audioPlayerContext.setAttribute("aria-hidden", String(!isLyrics));
  audioPlayerContext.inert = !isLyrics;
  audioPlayerContent.setAttribute("aria-hidden", String(isLyrics));
  audioPlayerContent.inert = isLyrics;
  audioLyricsPlayback.setAttribute("aria-hidden", String(!isLyrics));
  audioLyricsPlayback.inert = !isLyrics;
  audioContextColumns.hidden = false;
  renderAudioContextColumn("up-next");
  renderAudioContextColumn("suggested");

  if (isLyrics) {
    audioPlayerContextContent.replaceChildren(...audioPlaybackLyrics.map(([time, position, line]) => {
      const button = document.createElement("button");
      button.className = "audio-lyric";
      button.dataset.focus = "";
      button.dataset.audioLyricPosition = position;
      button.innerHTML = `<time>${time}</time><strong>${line}</strong>`;
      button.classList.toggle("is-current", Math.abs(audioPlayerPosition - position) < 10);
      return button;
    }));
  }
}

function setAudioBrowsing(enabled, focusTarget = null) {
  audioPlayerLayer.classList.toggle("is-audio-browsing", enabled);
  if (focusTarget) focusElement(focusTarget);
}

function resetAudioBrowsing() {
  audioPlayerLayer.classList.remove("is-audio-browsing");
}

function openAudioPlayerContext(mode, returnFocus) {
  audioPlayerContextReturnFocus = returnFocus || document.activeElement;
  if (mode === "lyrics") {
    resetAudioBrowsing();
    audioPlayerLayer.querySelectorAll(".audio-tools button").forEach((button) => {
      button.classList.toggle("is-context-open", button.dataset.audioPlayerControl === mode);
    });
    renderAudioPlayerContext(mode);
    audioPlayerLayer.classList.add("is-lyrics-active");
    focusElement(
      audioPlayerContextContent.querySelector(".is-current") ||
      audioPlayerContextContent.querySelector("[data-focus]"),
    );
    return;
  }

  renderAudioPlayerContext();
}

function closeAudioPlayerContext(restore = true) {
  if (!audioPlayerContextMode) return false;
  const returnFocus = audioPlayerContextReturnFocus;
  audioPlayerLayer.querySelectorAll(".audio-tools button").forEach((button) => button.classList.remove("is-context-open"));
  audioPlayerLayer.classList.remove("is-lyrics-active");
  renderAudioPlayerContext();
  if (restore) focusElement(returnFocus);
  audioPlayerContextReturnFocus = null;
  return true;
}

function parseAudioDuration(value = "4:12") {
  const [minutes, seconds] = value.split(":").map(Number);
  return Number.isFinite(minutes) && Number.isFinite(seconds) ? minutes * 60 + seconds : 252;
}

function updateAudioPlayerTimeline(delta = 0) {
  audioPlayerPosition = Math.min(100, Math.max(0, audioPlayerPosition + delta));
  const elapsed = audioPlayerDuration * (audioPlayerPosition / 100);
  const progress = `${audioPlayerPosition}%`;
  audioPlayerLayer.querySelectorAll("[data-audio-progress]").forEach((element) => {
    element.style.width = progress;
  });
  audioPlayerLayer.querySelectorAll("[data-audio-handle]").forEach((element) => {
    element.style.left = progress;
  });
  audioPlayerLayer.querySelectorAll("[data-audio-elapsed]").forEach((element) => {
    element.textContent = formatPlayerTime(elapsed);
  });
  audioPlayerLayer.querySelectorAll("[data-audio-duration]").forEach((element) => {
    element.textContent = formatPlayerTime(audioPlayerDuration);
  });
  audioPlayerLayer.querySelectorAll("[data-audio-player-timeline]").forEach((element) => {
    element.setAttribute("aria-label", `Playback position, ${Math.round(audioPlayerPosition)} percent`);
  });
  if (audioPlayerContextMode === "lyrics") {
    const activeLyric = audioPlaybackLyrics.findLast(([, position]) => audioPlayerPosition >= position);
    audioPlayerContextContent.querySelectorAll("[data-audio-lyric-position]").forEach((line) => {
      line.classList.toggle("is-current", Number(line.dataset.audioLyricPosition) === activeLyric?.[1]);
    });
  }
}

function applyAudioPlayerTrack(track, position = 0) {
  currentAudioTrack = track;
  const artwork = track.image || currentMedia.image || currentMedia.backdrop;
  audioPlayerBackdrop.style.backgroundImage = `url("${artwork}")`;
  audioPlayerCover.src = artwork;
  audioPlayerCover.alt = `${track.album || currentMedia.title} cover`;
  audioPlayerLayer.querySelectorAll("[data-audio-album]").forEach((element) => {
    element.textContent = track.album || currentMedia.title;
  });
  audioPlayerLayer.querySelectorAll("[data-audio-title]").forEach((element) => {
    element.textContent = track.title;
  });
  audioPlayerLayer.querySelectorAll("[data-audio-artist]").forEach((element) => {
    element.textContent = track.artist;
  });
  const primaryArtist = track.artist.split(" feat.")[0];
  const artistSeed = primaryArtist.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-").replaceAll(/^-|-$/g, "");
  audioPlayerLayer.querySelectorAll("[data-audio-artist-image]").forEach((element) => {
    element.src = `https://picsum.photos/seed/${artistSeed || "music-artist"}/160/160`;
    element.alt = `${primaryArtist} portrait`;
  });
  audioPlayerLayer.querySelectorAll("[data-audio-format]").forEach((element) => {
    element.replaceChildren(
      ...[track.format, track.bitDepth, track.sampleRate, track.channels].filter(Boolean).map((value) => {
        const span = document.createElement("span");
        span.textContent = value;
        return span;
      }),
    );
  });
  audioPlayerDuration = parseAudioDuration(track.duration);
  audioPlayerPosition = position;
  updateAudioPlayerTimeline();
}

function openAudioPlayer() {
  audioPlayerReturnToDetails = !detailLayer.hidden;
  previousFocus = document.activeElement;
  detailLayer.hidden = true;
  const track = currentAudioTrack || {
    title: ["Music album", "Music artist", "Playlist"].includes(currentMedia.kind) ? "First Light" : currentMedia.title,
    artist: currentMedia.kind === "Music artist"
      ? currentMedia.title
      : currentMedia.kind === "Playlist"
        ? "Marin Vale"
      : currentMedia.artist || selectedMusicItem?.dataset.artist || "Unknown artist",
    album: ["Music artist", "Playlist"].includes(currentMedia.kind) ? "Blue Hours" : currentMedia.title,
    image: ["Music artist", "Playlist"].includes(currentMedia.kind)
      ? "https://picsum.photos/seed/blue-hours-album/900/900"
      : currentMedia.image,
    duration: "4:12",
    format: "FLAC",
    bitDepth: "24-bit",
    sampleRate: "96 kHz",
    channels: "Stereo",
  };
  closeAudioPlayerContext(false);
  applyAudioPlayerTrack(track, 26);
  renderAudioPlayerContext();
  resetAudioBrowsing();
  const primary = audioPlayerLayer.querySelector('[data-audio-player-control="primary"]');
  primary.querySelector(".material-symbols-rounded").textContent = "pause";
  primary.setAttribute("aria-label", "Pause");
  audioPlayerLayer.hidden = false;
  focusElement(primary);
}

function closeAudioPlayer() {
  if (audioPlayerLayer.hidden) return false;
  closeAudioPlayerContext(false);
  resetAudioBrowsing();
  audioPlayerLayer.hidden = true;
  if (audioPlayerReturnToDetails) detailLayer.hidden = false;
  focusElement(previousFocus || document.querySelector("[data-view]:not([hidden]) [data-focus]"));
  return true;
}

function postPlayWordmark(title) {
  const words = title.split(" ");
  const lastWord = words.pop() || title;
  const firstLine = document.createElement("span");
  const secondLine = document.createElement("strong");
  firstLine.textContent = words.join(" ");
  secondLine.textContent = lastWord;
  document.querySelector("#post-play-wordmark").replaceChildren(firstLine, secondLine);
}

function resolvePostPlayItem() {
  if (currentMedia.kind === "Series") {
    const episodes = (activeSeriesData?.seasons || nightServiceSeasons)
      .flatMap((season) => season.episodes);
    const currentIndex = episodes.findIndex((episode) =>
      episode === currentEpisode ||
      (episode.number === currentEpisode?.number && episode.title === currentEpisode?.title));
    return {
      item: episodes[currentIndex >= 0 && currentIndex < episodes.length - 1 ? currentIndex + 1 : 0],
      kind: "episode",
    };
  }

  const films = mediaCatalog.filter((item) => item.type === "Film");
  const currentIndex = films.findIndex((item) => item.title === currentMedia.title);
  return {
    item: films[currentIndex >= 0 && currentIndex < films.length - 1 ? currentIndex + 1 : 0],
    kind: "film",
  };
}

function postPlayEpisodeState(episode) {
  if (episode.progress === 100) return `${episode.duration}, watched`;
  if (episode.progress > 0) return `${episode.duration}, ${episode.progress}% watched`;
  return episode.duration;
}

function selectPostPlayEpisode(index) {
  const episode = postPlayEpisodeSeason?.episodes[index];
  const button = postPlayEpisodesList.querySelector(`[data-post-play-episode="${index}"]`);
  if (!episode || !button) return;

  document.querySelector("#post-play-episodes-number").textContent = episode.number;
  document.querySelector("#post-play-episodes-preview-title").textContent = episode.title;
  document.querySelector("#post-play-episodes-preview-summary").textContent = episode.summary;
  document.querySelector("#post-play-episodes-preview-duration").textContent = episode.duration;
  document.querySelector("#post-play-episodes-preview-date").textContent = episode.date;
  document.querySelector("#post-play-episodes-preview-quality").textContent = episode.quality || "HD";
  document.querySelector("#player-image").style.backgroundImage = `url("${episode.image}")`;
  button.scrollIntoView({ block: "nearest" });
}

function renderPostPlayEpisodes() {
  const seasons = activeSeriesData?.seasons || nightServiceSeasons;
  const targetEpisode = postPlayNextItem?.item || currentEpisode;
  postPlayEpisodeSeason =
    seasons.find((season) =>
      season.episodes.some((episode) =>
        episode === targetEpisode ||
        (episode.number === targetEpisode?.number && episode.title === targetEpisode?.title))) ||
    seasons[0];

  document.querySelector("#post-play-episodes-season").textContent = postPlayEpisodeSeason.label;
  const buttons = postPlayEpisodeSeason.episodes.map((episode, index) => {
    const button = document.createElement("button");
    button.className = "post-play-episode";
    button.dataset.focus = "";
    button.dataset.postPlayEpisode = String(index);
    button.setAttribute("aria-label", `${episode.number}, ${episode.title}, ${postPlayEpisodeState(episode)}`);

    const number = document.createElement("span");
    number.className = "post-play-episode__number";
    number.textContent = episode.number;
    const title = document.createElement("strong");
    title.textContent = episode.title;
    const state = document.createElement("span");
    state.className = "post-play-episode__state";
    state.textContent = postPlayEpisodeState(episode);
    const play = document.createElement("span");
    play.className = "post-play-episode__play";
    play.setAttribute("aria-hidden", "true");
    play.textContent = episode.progress === 100 ? "replay" : "play_arrow";
    button.append(number, title, state, play);
    return button;
  });
  postPlayEpisodesList.replaceChildren(...buttons);
}

function openPostPlayEpisodes() {
  if (currentMedia.kind !== "Series") return false;
  renderPostPlayEpisodes();
  postPlay.classList.add("is-episodes");
  postPlay.setAttribute("aria-labelledby", "post-play-episodes-title");
  postPlayEpisodes.hidden = false;

  const targetEpisode = postPlayNextItem?.item;
  const targetIndex = Math.max(0, postPlayEpisodeSeason.episodes.findIndex((episode) =>
    episode === targetEpisode ||
    (episode.number === targetEpisode?.number && episode.title === targetEpisode?.title)));
  const target = postPlayEpisodesList.querySelector(`[data-post-play-episode="${targetIndex}"]`);
  selectPostPlayEpisode(targetIndex);
  focusElement(target);
  return true;
}

function closePostPlayEpisodes(restoreFocus = true) {
  if (postPlayEpisodes.hidden) return false;
  postPlay.classList.remove("is-episodes");
  postPlay.setAttribute("aria-labelledby", "post-play-title");
  postPlayEpisodes.hidden = true;
  const nextImage = postPlayNextItem?.item?.image || postPlayNextItem?.item?.backdrop;
  if (nextImage) {
    document.querySelector("#player-image").style.backgroundImage = `url("${nextImage}")`;
  }
  if (restoreFocus) {
    focusElement(postPlay.querySelector('[data-post-play-action="details"]'));
  }
  return true;
}

function playPostPlayEpisode(index) {
  const episode = postPlayEpisodeSeason?.episodes[index];
  if (!episode) return;
  const shouldReturnToDetails = playerReturnToDetails;
  const returnFocus = previousFocus;
  currentEpisode = episode;
  closePostPlayEpisodes(false);
  closePostPlay(false);
  openPlayer();
  playerReturnToDetails = shouldReturnToDetails;
  previousFocus = returnFocus;
}

function openPostPlay() {
  if (mediaMode(currentMedia.kind) !== "video") return;

  const completedItem = currentMedia.kind === "Series" ? currentEpisode : currentMedia;
  const resolved = resolvePostPlayItem();
  postPlayNextItem = resolved;
  const nextItem = resolved.item;
  const isEpisode = resolved.kind === "episode";
  const nextMeta = isEpisode
    ? {
        series: currentMedia.title,
        episode: nextItem.number,
        duration: nextItem.duration,
        quality: nextItem.quality || "HD",
        audio: nextItem.audio || "English 5.1",
        image: nextItem.image,
      }
    : {
        series: "Recommended next",
        episode: nextItem.year,
        duration: nextItem.meta.split("|")[1],
        quality: nextItem.meta.includes("4K")
          ? "4K HDR"
          : nextItem.meta.includes("HDR")
            ? "HDR"
            : "HD",
        audio: "English 5.1",
        image: nextItem.backdrop,
      };

  document.querySelector("#post-play-series").textContent =
    isEpisode ? currentMedia.title : "Now finished";
  document.querySelector("#post-play-completed").textContent =
    `${completedItem?.title || currentMedia.title} complete`;
  document.querySelector("#post-play-episode").textContent = nextMeta.episode;
  document.querySelector("#post-play-title").textContent = nextItem.title;
  document.querySelector("#post-play-summary").textContent = nextItem.summary;
  document.querySelector("#post-play-duration").textContent = nextMeta.duration;
  document.querySelector("#post-play-quality").textContent = nextMeta.quality;
  document.querySelector("#post-play-audio").textContent = nextMeta.audio;
  document.querySelector(".post-play-action--primary").setAttribute(
    "aria-label",
    isEpisode ? "Play next episode" : "Play recommended movie",
  );
  const episodesAction = postPlay.querySelector('[data-post-play-action="details"]');
  episodesAction.hidden = !isEpisode;
  postPlayWordmark(isEpisode ? currentMedia.title : nextItem.title);

  document.querySelector("#player-image").style.backgroundImage = `url("${nextMeta.image}")`;
  playerDrawer.hidden = true;
  playerLayer.hidden = false;
  playerLayer.classList.remove("is-osd-hidden");
  playerLayer.classList.add("is-post-play");
  playerLayer.querySelector(".player-osd").inert = true;
  postPlay.classList.remove("is-episodes");
  postPlay.setAttribute("aria-labelledby", "post-play-title");
  postPlayEpisodes.hidden = true;
  postPlay.hidden = false;
  focusElement(postPlay.querySelector('[data-post-play-action="next"]'));
}

function closePostPlay(restorePlayback = true) {
  if (postPlay.hidden) return false;
  postPlay.classList.remove("is-episodes");
  postPlay.setAttribute("aria-labelledby", "post-play-title");
  postPlayEpisodes.hidden = true;
  postPlay.hidden = true;
  playerLayer.classList.remove("is-post-play");
  playerLayer.querySelector(".player-osd").inert = false;

  if (restorePlayback) {
    const playbackImage =
      (currentMedia.kind === "Series" ? currentEpisode?.image : currentMedia.backdrop) ||
      currentMedia.backdrop;
    document.querySelector("#player-image").style.backgroundImage = `url("${playbackImage}")`;
    setPlayerOsdVisible(true);
  }
  return true;
}

function activatePostPlayAction(action) {
  if (action === "next") {
    const shouldReturnToDetails = playerReturnToDetails;
    const returnFocus = previousFocus;
    if (postPlayNextItem?.kind === "episode") {
      currentEpisode = postPlayNextItem.item;
    } else if (postPlayNextItem?.item) {
      currentMedia = { ...postPlayNextItem.item, kind: "Film" };
    }
    closePostPlay(false);
    openPlayer();
    playerReturnToDetails = shouldReturnToDetails;
    previousFocus = returnFocus;
  } else if (action === "replay") {
    closePostPlay(true);
    playerPosition = 0;
    updatePlayerTimeline();
  } else if (action === "details") {
    if (!openPostPlayEpisodes()) {
      closePostPlay(false);
      closePlayer();
      if (detailLayer.hidden) openDetails();
    }
  }
}

function openPlayerCheckin() {
  const playbackEpisode = currentMedia.kind === "Series" ? currentEpisode : null;
  document.querySelector("#player-checkin-series").textContent =
    currentMedia.kind === "Series" ? currentMedia.title : "Now playing";
  document.querySelector("#player-checkin-media").textContent = playbackEpisode
    ? `${playbackEpisode.number}, ${playbackEpisode.title}`
    : currentMedia.title;

  closePostPlay(false);
  closePlayerRecovery(false);
  playerDrawer.hidden = true;
  playerLayer.hidden = false;
  playerLayer.classList.remove("is-osd-hidden", "is-post-play", "is-recovery");
  playerLayer.classList.add("is-checkin");
  playerLayer.querySelector(".player-osd").inert = true;
  playerCheckin.hidden = false;
  focusElement(playerCheckin.querySelector('[data-player-checkin-action="continue"]'));
}

function closePlayerCheckin(restorePlayback = true) {
  if (playerCheckin.hidden) return false;
  playerCheckin.hidden = true;
  playerLayer.classList.remove("is-checkin");
  playerLayer.querySelector(".player-osd").inert = false;
  if (restorePlayback) setPlayerOsdVisible(true);
  return true;
}

function stopPlayerCheckin() {
  if (playerCheckin.hidden) return false;
  closePlayerCheckin(false);
  closePlayer();
  if (detailLayer.hidden) openDetails();
  return true;
}

function activatePlayerCheckinAction(button) {
  if (button.dataset.playerCheckinAction === "continue") {
    closePlayerCheckin(true);
  } else {
    stopPlayerCheckin();
  }
}

function openPlayerRecovery() {
  clearTimeout(playerRecoveryTimer);
  const playbackEpisode = currentMedia.kind === "Series" ? currentEpisode : null;
  document.querySelector("#player-recovery-media").textContent = playbackEpisode
    ? `${currentMedia.title}, ${playbackEpisode.title}`
    : currentMedia.title;

  closePostPlay(false);
  closePlayerCheckin(false);
  playerDrawer.hidden = true;
  playerLayer.hidden = false;
  playerLayer.classList.remove("is-osd-hidden", "is-post-play");
  playerLayer.classList.add("is-recovery");
  playerLayer.querySelector(".player-osd").inert = true;
  playerRecovery.hidden = false;
  const retry = playerRecovery.querySelector('[data-player-recovery-action="retry"]');
  retry.classList.remove("is-loading");
  retry.removeAttribute("aria-busy");
  retry.querySelector(".material-symbols-rounded").textContent = "refresh";
  retry.querySelector(".player-recovery-action__label").textContent = "Retry";
  focusElement(retry);
}

function closePlayerRecovery(restorePlayback = true) {
  if (playerRecovery.hidden) return false;
  clearTimeout(playerRecoveryTimer);
  playerRecovery.hidden = true;
  playerLayer.classList.remove("is-recovery");
  playerLayer.querySelector(".player-osd").inert = false;
  if (restorePlayback) setPlayerOsdVisible(true);
  return true;
}

function retryPlayerRecovery(button) {
  if (button.classList.contains("is-loading")) return;
  button.classList.add("is-loading");
  button.setAttribute("aria-busy", "true");
  button.querySelector(".material-symbols-rounded").textContent = "progress_activity";
  button.querySelector(".player-recovery-action__label").textContent = "Retrying";
  playerRecoveryTimer = setTimeout(() => {
    closePlayerRecovery(true);
    showToast("Playback resumed");
  }, 900);
}

function activatePlayerRecoveryAction(button) {
  const action = button.dataset.playerRecoveryAction;
  if (action === "retry") {
    retryPlayerRecovery(button);
  } else if (action === "back") {
    closePlayerRecovery(false);
    closePlayer();
    if (detailLayer.hidden) openDetails();
  }
}

function openPlayer() {
  if (mediaMode(currentMedia.kind) === "audio") {
    openAudioPlayer();
    return;
  }
  playerReturnToDetails = !detailLayer.hidden;
  previousFocus = document.activeElement;
  detailLayer.hidden = true;
  const playbackEpisode = currentMedia.kind === "Series" ? currentEpisode : null;
  const mode = mediaMode(currentMedia.kind);
  const playbackAudio = mode === "audio" ? currentAudioTrack : null;
  document.querySelector("#player-title").textContent =
    playbackEpisode?.title || playbackAudio?.title || currentMedia.title;
  const playbackImage = playbackEpisode?.image || currentMedia.backdrop;
  document.querySelector("#player-image").style.backgroundImage = `url("${playbackImage}")`;
  playerTimelinePreviewFrame.style.backgroundImage = `url("${playbackImage}")`;
  playerLayer.dataset.mode = mode;
  playerLayer.classList.toggle("is-series-playback", currentMedia.kind === "Series");
  playerLayer.classList.remove("is-osd-hidden", "is-post-play", "is-recovery", "is-checkin");
  postPlay.classList.remove("is-episodes");
  postPlayEpisodes.hidden = true;
  postPlay.hidden = true;
  playerRecovery.hidden = true;
  playerCheckin.hidden = true;
  playerLayer.querySelector(".player-osd").inert = false;
  playerMiniSeek.classList.remove("is-visible");
  playerMiniSeek.setAttribute("aria-hidden", "true");
  clearTimeout(playerMiniSeekTimer);
  clearTimeout(playerExitTimer);
  clearTimeout(playerRecoveryTimer);
  const exitControl = playerLayer.querySelector("[data-player-exit]");
  exitControl.classList.remove("is-armed");
  exitControl.querySelector(".player-control__label").textContent = "Back";
  playerDrawer.hidden = true;
  renderPlayerWordmark();
  document.querySelector("#player-kicker").textContent =
    playbackEpisode
      ? `${currentMedia.title} · ${playbackEpisode.number}`
      : playbackAudio
        ? `${currentMedia.title} · ${playbackAudio.artist}`
        : mode === "photo"
          ? "Photo viewer"
          : mode === "audio"
            ? "Now listening"
          : currentMedia.kind === "Live TV"
            ? "Live TV"
            : "Now playing";
  document.querySelector("#player-quality").textContent =
    playbackEpisode?.quality ||
    (mode === "photo"
      ? "Original"
      : mode === "audio"
        ? "Lossless"
        : currentMedia.meta.includes("4K")
          ? "4K HDR"
          : "HD");
  document.querySelector("#player-audio").textContent = playbackEpisode?.audio || "English 5.1";
  document.querySelector("#player-subtitles").textContent = "English subtitles";
  const controlSets = {
    video: [
      ["previous", "replay_10", "Rewind 10 seconds"],
      ["primary", "pause", "Pause"],
      ["next", "forward_10", "Forward 10 seconds"],
      ["secondary-one", "subtitles", "Subtitles"],
      ["secondary-two", "graphic_eq", "Audio tracks"],
    ],
    audio: [
      ["previous", "skip_previous", "Previous track"],
      ["primary", "pause", "Pause"],
      ["next", "skip_next", "Next track"],
      ["secondary-one", "queue_music", "Play queue"],
      ["secondary-two", "lyrics", "Lyrics"],
    ],
    photo: [
      ["previous", "arrow_back", "Previous photo"],
      ["primary", "slideshow", "Start slideshow"],
      ["next", "arrow_forward", "Next photo"],
      ["secondary-one", "info", "Photo information"],
      ["secondary-two", "fit_screen", "Fit to screen"],
    ],
  };
  controlSets[mode].forEach(([role, icon, label]) => {
    const control = playerLayer.querySelector(`[data-player-control="${role}"]`);
    control.setAttribute("aria-label", label);
    control.querySelector(".material-symbols-rounded").textContent = icon;
    control.querySelector(".player-control__label").textContent = label
      .replace(" 10 seconds", "")
      .replace(" tracks", "");
  });
  playerPosition = playbackEpisode?.progress > 0 && playbackEpisode.progress < 100
    ? playbackEpisode.progress
    : 72;
  updatePlayerTimeline();
  playerLayer.hidden = false;
  focusElement(playerLayer.querySelector(".player-control--primary"));
}

function requestPlayerExit() {
  const exitControl = playerLayer.querySelector("[data-player-exit]");
  if (exitControl.classList.contains("is-armed")) {
    clearTimeout(playerExitTimer);
    closePlayer();
    return;
  }
  exitControl.classList.add("is-armed");
  exitControl.querySelector(".player-control__label").textContent = "Press again";
  exitControl.setAttribute("aria-label", "Press again to leave playback");
  playerExitTimer = setTimeout(() => {
    exitControl.classList.remove("is-armed");
    exitControl.querySelector(".player-control__label").textContent = "Back";
    exitControl.setAttribute("aria-label", "Press twice to leave playback");
  }, 1400);
}

function closePlayer() {
  if (playerLayer.hidden) return false;
  clearTimeout(playerMiniSeekTimer);
  clearTimeout(playerExitTimer);
  clearTimeout(playerRecoveryTimer);
  playerLayer.hidden = true;
  playerDrawer.hidden = true;
  postPlay.classList.remove("is-episodes");
  postPlayEpisodes.hidden = true;
  postPlay.hidden = true;
  playerRecovery.hidden = true;
  playerCheckin.hidden = true;
  playerLayer.querySelector(".player-osd").inert = false;
  playerLayer.classList.remove("is-osd-hidden", "is-post-play", "is-recovery", "is-checkin");
  playerMiniSeek.classList.remove("is-visible");
  if (playerReturnToDetails) detailLayer.hidden = false;
  focusElement(previousFocus || document.querySelector("[data-view]:not([hidden]) [data-focus]"));
  return true;
}

function closeOverlays(restore = true) {
  const hadOverlay =
    !detailLayer.hidden ||
    !personLayer.hidden ||
    !audioPlayerLayer.hidden ||
    !playerLayer.hidden ||
    !stateLayer.hidden;
  detailLayer.hidden = true;
  personLayer.hidden = true;
  audioPlayerLayer.hidden = true;
  playerLayer.hidden = true;
  playerDrawer.hidden = true;
  stateLayer.hidden = true;
  trackActions.hidden = true;
  detailMusic.classList.remove("is-action-mode");
  detailArtist.classList.remove("is-action-mode");
  resetAudioBrowsing();
  audioPlayerLayer.classList.remove("is-lyrics-active");
  audioPlayerContext.classList.remove("is-active");
  audioPlayerContext.setAttribute("aria-hidden", "true");
  audioPlayerContext.inert = true;
  audioPlayerContent.setAttribute("aria-hidden", "false");
  audioPlayerContent.inert = false;
  audioLyricsPlayback.setAttribute("aria-hidden", "true");
  audioLyricsPlayback.inert = true;
  audioPlayerLayer.querySelectorAll(".audio-tools button").forEach((button) => button.classList.remove("is-context-open"));
  audioPlayerContextMode = null;
  audioPlayerContextReturnFocus = null;
  trackActionReturnFocus = null;
  trackActionContainer = null;
  if (restore && hadOverlay) focusElement(previousFocus || document.querySelector("[data-view]:not([hidden]) [data-focus]"));
  return hadOverlay;
}

function showToast(message) {
  clearTimeout(toastTimer);
  toast.textContent = message;
  toast.hidden = false;
  toastTimer = setTimeout(() => { toast.hidden = true; }, 2400);
}

function quickConnectCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const values = new Uint8Array(6);
  crypto.getRandomValues(values);
  return [...values].map((value) => alphabet[value % alphabet.length]).join("");
}

function refreshQuickConnect(button) {
  if (button.classList.contains("is-loading")) return;
  const icon = button.querySelector(".material-symbols-rounded");
  const label = button.querySelector(".login-quick__label");
  button.classList.add("is-loading");
  button.classList.remove("has-code");
  button.setAttribute("aria-busy", "true");
  icon.textContent = "progress_activity";

  setTimeout(() => {
    label.textContent = quickConnectCode();
    icon.textContent = "key";
    button.classList.remove("is-loading");
    button.classList.add("has-code");
    button.removeAttribute("aria-busy");
    button.setAttribute("aria-label", "Generate a new Quick Connect code");
  }, 650);
}

function showState(type) {
  previousFocus = document.activeElement;
  if (type === "loading") {
    stateLayer.innerHTML = `
      <div class="skeleton-state">
        <div class="skeleton-title"></div>
        <div class="skeleton-grid">${"<div class=\"skeleton-card\"></div>".repeat(5)}</div>
      </div>
    `;
  } else {
    const empty = type === "empty";
    stateLayer.innerHTML = `
      <div class="state-message">
        <span class="material-symbols-rounded">${empty ? "movie_off" : "cloud_off"}</span>
        <h2>${empty ? "Nothing here yet" : "Media server unavailable"}</h2>
        <p>${empty ? "Add media to this library, then refresh to see it here." : "Check the server address and network connection, then try again."}</p>
        <button class="button button--primary" data-focus>${empty ? "Refresh library" : "Try again"}</button>
      </div>
    `;
  }
  stateLayer.hidden = false;
  if (type === "loading") {
    setTimeout(() => closeOverlays(true), 1800);
  } else {
    focusElement(stateLayer.querySelector("[data-focus]"));
  }
}

document.addEventListener("focusin", (event) => {
  document.querySelectorAll(".is-key-focused").forEach((node) => {
    if (node !== event.target) node.classList.remove("is-key-focused");
  });
  if (!audioPlayerLayer.hidden) {
    setAudioBrowsing(
      !audioPlayerContextMode && Boolean(event.target.closest("#audio-context-columns")),
    );
  }
  if (event.target.matches("[data-media]")) {
    setCurrentMedia(event.target);
    if (activeView === "home" && event.target.closest('[data-view="home"]')) {
      const inFirstRail = Boolean(event.target.closest(".media-section--continue"));
      setHomeBrowsing(!inFirstRail, false, !inFirstRail);
    }
  } else if (activeView === "home" && event.target.closest(".top-nav, .topbar__actions, .hero__actions")) {
    setHomeBrowsing(false);
  }
  const liveProgram = event.target.closest("[data-live-program]");
  if (activeView === "live") {
    document.querySelector('[data-view="live"]').classList.toggle("is-guide-focused", Boolean(liveProgram));
  }
  if (liveProgram) {
    selectLiveProgram(
      Number(liveProgram.dataset.channelIndex),
      Number(liveProgram.dataset.programIndex),
    );
  }
  const musicItem = event.target.closest("[data-music-item]");
  if (musicItem) {
    selectMusicItem(musicItem);
    setMusicBrowsing(true);
  } else if (
    activeView === "music" &&
    event.target.closest(".top-nav, .music-heading, .music-hero__actions")
  ) {
    setMusicBrowsing(false);
  }
  const postPlayEpisode = event.target.closest("[data-post-play-episode]");
  if (postPlayEpisode && !postPlayEpisodes.hidden) {
    selectPostPlayEpisode(Number(postPlayEpisode.dataset.postPlayEpisode));
  }
});

document.addEventListener("click", (event) => {
  const nav = event.target.closest("[data-nav]");
  if (nav?.dataset.library) {
    activeLibraryId = nav.dataset.library;
    activeLibraryView = "all";
    document.querySelectorAll("[data-library-view]").forEach((item) => {
      item.classList.toggle("is-active", item.dataset.libraryView === "all");
    });
    renderLibraryMedia();
  }
  if (nav) showView(nav.dataset.nav);

  if (event.target.closest("[data-profile-switch]")) {
    profileReturnView = activeView;
    showView("profiles");
  }

  if (event.target.closest("[data-profile-back]")) showView(profileReturnView);

  const searchType = event.target.closest("[data-search-type]");
  if (searchType) {
    activeSearchType = searchType.dataset.searchType;
    document.querySelectorAll("[data-search-type]").forEach((item) => {
      item.classList.toggle("is-active", item === searchType);
    });
    renderSearchResults();
  }

  const libraryView = event.target.closest("[data-library-view]");
  if (libraryView) {
    activeLibraryView = libraryView.dataset.libraryView;
    document.querySelectorAll("[data-library-view]").forEach((item) => {
      item.classList.toggle("is-active", item === libraryView);
    });
    renderLibraryMedia();
  }

  const librarySort = event.target.closest("[data-library-sort]");
  if (librarySort) {
    librarySortMode = librarySortMode === "title" ? "newest" : "title";
    librarySort.querySelector(".library-sort__label").textContent =
      librarySortMode === "title" ? "Title" : "Newest";
    librarySort.querySelector(".material-symbols-rounded").textContent =
      librarySortMode === "title" ? "sort_by_alpha" : "calendar_today";
    renderLibraryMedia();
  }

  const liveView = event.target.closest("[data-live-view]");
  if (liveView) {
    document.querySelectorAll("[data-live-view]").forEach((item) => {
      item.classList.toggle("is-active", item === liveView);
    });
    if (liveView.dataset.liveView !== "now") {
      showToast(liveView.dataset.liveView === "guide" ? "Full guide" : "Recordings");
    }
  }

  const liveProgram = event.target.closest("[data-live-program]");
  if (liveProgram && selectedLiveProgram) {
    if (selectedLiveProgram.program.state === "now") {
      currentMedia = {
        title: selectedLiveProgram.program.title,
        meta: `Live now|HD|${selectedLiveProgram.program.genre}`,
        summary: selectedLiveProgram.program.summary,
        backdrop: selectedLiveProgram.program.backdrop,
        kind: "Live TV",
        hasLogo: true,
      };
      openPlayer();
    } else {
      showToast("Recording scheduled");
    }
  }

  if (event.target.closest("[data-live-primary]") && selectedLiveProgram) {
    if (selectedLiveProgram.program.state === "now") {
      currentMedia = {
        title: selectedLiveProgram.program.title,
        meta: `Live now|HD|${selectedLiveProgram.program.genre}`,
        summary: selectedLiveProgram.program.summary,
        backdrop: selectedLiveProgram.program.backdrop,
        kind: "Live TV",
        hasLogo: true,
      };
      openPlayer();
    } else {
      showToast("Recording scheduled");
    }
  }

  if (event.target.closest("[data-live-record]")) showToast("Recording scheduled");

  const musicView = event.target.closest("[data-music-view]");
  if (musicView) {
    document.querySelectorAll("[data-music-view]").forEach((item) => {
      item.classList.toggle("is-active", item === musicView);
    });
    const target = musicView.dataset.musicTarget && document.querySelector(`#${musicView.dataset.musicTarget}`);
    if (target) {
      const firstItem = target.querySelector("[data-music-item]");
      if (firstItem) focusElement(firstItem);
    } else {
      content.scrollTo({ top: 0, behavior: "smooth" });
    }
  }

  if (event.target.closest("[data-music-primary]") && selectedMusicItem) {
    setCurrentMedia(selectedMusicItem);
    currentAudioTrack = null;
    openPlayer();
  }

  if (event.target.closest("[data-music-shuffle]") && selectedMusicItem) {
    setCurrentMedia(selectedMusicItem);
    currentAudioTrack = null;
    showToast("Queue shuffled");
    openPlayer();
  }

  if (event.target.closest("[data-music-more]")) showToast("Music options");

  const audioTrack = event.target.closest("[data-audio-track]");
  if (audioTrack) {
    currentAudioTrack = {
      title: audioTrack.dataset.trackTitle,
      artist: audioTrack.dataset.trackArtist,
      duration: audioTrack.dataset.trackDuration,
      album: audioTrack.dataset.trackAlbum,
      image: audioTrack.dataset.trackImage,
      format: audioTrack.dataset.trackFormat,
      bitDepth: audioTrack.dataset.trackBitDepth,
      sampleRate: audioTrack.dataset.trackSampleRate,
      channels: audioTrack.dataset.trackChannels,
    };
    openPlayer();
  }

  const trackAction = event.target.closest("[data-track-action]");
  if (trackAction) {
    const selectedTrack = trackActionReturnFocus;
    const action = trackAction.dataset.trackAction;
    if (action === "queue") showToast("Playing next");
    if (action === "playlist") showToast("Added to playlist");
    if (action === "lyrics") showToast(selectedTrack?.dataset.trackLyrics || "No lyrics available");
    if (action === "details") {
      showToast([
        selectedTrack?.dataset.trackFormat,
        selectedTrack?.dataset.trackBitDepth,
        selectedTrack?.dataset.trackSampleRate,
        selectedTrack?.dataset.trackChannels,
      ].filter(Boolean).join(" · "));
    }
    if (action === "favorite") {
      const isFavorite = selectedTrack?.dataset.trackFavorite !== "true";
      if (selectedTrack) selectedTrack.dataset.trackFavorite = String(isFavorite);
      trackAction.classList.toggle("is-selected", isFavorite);
      showToast(isFavorite ? "Favorited" : "Favorite removed");
    }
  }

  if (event.target.closest("[data-music-detail-shuffle]")) {
    currentAudioTrack = null;
    showToast("Album shuffled");
    openPlayer();
  }

  const personCard = event.target.closest("[data-person]");
  if (personCard) {
    openPerson(personCard);
  }

  if (event.target.closest("[data-close-person]")) closePerson(true);
  if (event.target.closest("[data-person-favorite]")) showToast("Person favorited");

  const personTitle = event.target.closest("[data-person-media-index], [data-person-credit]");
  if (personTitle) {
    openPersonTitle(Number(
      personTitle.dataset.personMediaIndex ?? personTitle.dataset.personCredit,
    ));
  }

  const media = event.target.closest("[data-media]");
  if (media) {
    setCurrentMedia(media);
    openDetails();
  }

  const episode = event.target.closest("[data-episode-play]");
  if (episode) {
    if (episode.dataset.seasonIndex !== undefined) {
      currentEpisode =
        activeSeriesData?.seasons[Number(episode.dataset.seasonIndex)]
          ?.episodes[Number(episode.dataset.episodeIndex)] ||
        currentEpisode;
    }
    openPlayer();
  }

  const action = event.target.closest("[data-action]")?.dataset.action;
  if (action === "play") openPlayer();
  if (action === "details") openDetails();
  if (action === "favorite") showToast("Added to your list");

  if (event.target.closest("[data-close-audio-player]")) closeAudioPlayer();
  if (event.target.closest("[data-close-player]")) closePlayer();
  if (event.target.closest("[data-player-exit]")) requestPlayerExit();
  if (event.target.closest("[data-close-player-drawer]")) backPlayerDrawer();
  if (event.target.closest("#player-timeline")) {
    showToast(`Playing from ${document.querySelector("#player-timeline-preview-time").textContent}`);
    focusElement(playerLayer.querySelector(".player-control--primary"));
  }

  const audioPlayerControl = event.target.closest("[data-audio-player-control]");
  if (audioPlayerControl) {
    const role = audioPlayerControl.dataset.audioPlayerControl;
    if (role === "primary") {
      const icon = audioPlayerControl.querySelector(".material-symbols-rounded");
      const paused = icon.textContent.trim() === "play_arrow";
      audioPlayerLayer.querySelectorAll('[data-audio-player-control="primary"]').forEach((button) => {
        button.querySelector(".material-symbols-rounded").textContent = paused ? "pause" : "play_arrow";
        const label = button.querySelector(".audio-control-label");
        if (label) label.textContent = paused ? "Pause" : "Play";
        button.setAttribute("aria-label", paused ? "Pause" : "Play");
      });
    } else if (role === "previous" || role === "next") {
      const currentIndex = Math.max(0, audioPlaybackQueue.findIndex((track) => track.title === currentAudioTrack?.title));
      const offset = role === "previous" ? -1 : 1;
      const nextTrack = audioPlaybackQueue[
        (currentIndex + offset + audioPlaybackQueue.length) % audioPlaybackQueue.length
      ];
      applyAudioPlayerTrack(nextTrack);
      renderAudioPlayerContext(audioPlayerContextMode);
      showToast(role === "previous" ? "Previous track" : "Next track");
    } else if (role === "shuffle" || role === "repeat") {
      const enabled = !audioPlayerControl.classList.contains("is-active");
      audioPlayerLayer.querySelectorAll(`[data-audio-player-control="${role}"]`).forEach((button) => {
        button.classList.toggle("is-active", enabled);
      });
      showToast(`${role === "shuffle" ? "Shuffle" : "Repeat"} ${enabled ? "on" : "off"}`);
    } else if (role === "queue") {
      const focusQueue = () => focusElement(
        audioUpNextContent.querySelector("[data-focus]") ||
        audioContextColumns.querySelector('[data-audio-context-view="up-next"]'),
      );
      if (audioPlayerContextMode === "lyrics") {
        focusQueue();
      } else if (audioContextColumns.hidden) {
        setTimeout(focusQueue, 760);
      } else {
        focusQueue();
      }
    } else if (role === "lyrics") {
      if (audioPlayerContextMode === "lyrics") {
        closeAudioPlayerContext(true);
      } else {
        if (audioPlayerContextMode) closeAudioPlayerContext(false);
        openAudioPlayerContext("lyrics", audioPlayerControl);
      }
    }
  }

  const audioContextView = event.target.closest("[data-audio-context-view]");
  if (audioContextView) {
    const type = audioContextView.dataset.audioContextView;
    audioContextViews[type] = audioContextViews[type] === "covers" ? "tracks" : "covers";
    renderAudioContextColumn(type);
    focusElement(audioContextColumns.querySelector(`[data-audio-context-view="${type}"]`));
  }

  const audioContextTrack = event.target.closest("[data-audio-context-source]");
  if (audioContextTrack) {
    const type = audioContextTrack.dataset.audioContextSource;
    const index = Number(audioContextTrack.dataset.audioContextIndex);
    const track = audioContextTracks(type)[index];
    if (track) {
      applyAudioPlayerTrack(track);
      renderAudioPlayerContext(audioPlayerContextMode);
      focusElement(
        audioContextColumns
          .querySelector(`[data-audio-context-column="${type}"]`)
          ?.querySelectorAll(".audio-context-column__content [data-focus]")[index],
      );
    }
  }

  const lyric = event.target.closest("[data-audio-lyric-position]");
  if (lyric) {
    audioPlayerPosition = Number(lyric.dataset.audioLyricPosition);
    updateAudioPlayerTimeline();
    renderAudioPlayerContext("lyrics");
    focusElement(
      audioPlayerContextContent.querySelector(`[data-audio-lyric-position="${lyric.dataset.audioLyricPosition}"]`),
    );
  }

  if (event.target.closest("[data-post-play-episodes-back]")) {
    closePostPlayEpisodes(true);
  }

  const postPlayEpisode = event.target.closest("[data-post-play-episode]");
  if (postPlayEpisode) {
    playPostPlayEpisode(Number(postPlayEpisode.dataset.postPlayEpisode));
  }

  const playerCheckinAction = event.target.closest("[data-player-checkin-action]");
  if (playerCheckinAction) {
    activatePlayerCheckinAction(playerCheckinAction);
  }

  const playerRecoveryAction = event.target.closest("[data-player-recovery-action]");
  if (playerRecoveryAction) {
    activatePlayerRecoveryAction(playerRecoveryAction);
  }

  const postPlayAction = event.target.closest("[data-post-play-action]");
  if (postPlayAction) {
    activatePostPlayAction(postPlayAction.dataset.postPlayAction);
  }

  const playerControl = event.target.closest("[data-player-control]");
  if (playerControl) {
    const role = playerControl.dataset.playerControl;
    if (role === "primary") {
      const icon = playerControl.querySelector(".material-symbols-rounded");
      const label = playerControl.querySelector(".player-control__label");
      const isPaused = icon.textContent.trim() === "play_arrow";
      icon.textContent = isPaused ? "pause" : "play_arrow";
      label.textContent = isPaused ? "Pause" : "Play";
      playerControl.setAttribute("aria-label", isPaused ? "Pause" : "Play");
    } else if (role === "previous" || role === "next") {
      seekPlayerSeconds(role === "previous" ? -10 : 10);
    } else if (role === "secondary-one") {
      openPlayerDrawer("subtitles", playerControl);
    } else if (role === "secondary-two") {
      openPlayerDrawer("audio", playerControl);
    } else if (role === "chapters") {
      openPlayerDrawer("chapters", playerControl);
    } else if (role === "more") {
      openPlayerDrawer("more", playerControl);
    } else if (role === "previous-item" || role === "next-item") {
      showToast(role === "previous-item" ? "Previous episode" : "Next episode");
    }
  }

  const playerOption = event.target.closest("[data-player-option]");
  if (playerOption) {
    const optionType = playerOption.dataset.playerOptionType;
    const value = playerOption.dataset.playerOptionValue;
    if (playerOption.classList.contains("is-navigable")) {
      openPlayerDrawer(playerOption.dataset.playerOptionTarget, null, playerOption);
    } else if (playerOption.classList.contains("is-informational")) {
      showToast(`${value} · ${playerOption.querySelector("small").textContent}`);
    } else {
      playerDrawerOptions.querySelectorAll(".player-option").forEach((item) => {
        item.classList.toggle("is-selected", item === playerOption);
        item.querySelector(".material-symbols-rounded").textContent = item === playerOption ? "check" : "";
      });
      const settingKey = playerOption.dataset.playerSettingKey;
      if (settingKey) {
        playbackOptions[settingKey] = value;
        showToast(`${playerDrawerConfig(optionType).title} · ${value}`);
      }
      if (optionType === "audio") document.querySelector("#player-audio").textContent = value;
      if (optionType === "subtitles") {
        document.querySelector("#player-subtitles").textContent =
          value === "Off" ? "Subtitles off" : `${value} subtitles`;
      }
      if (optionType === "chapters") {
        const chapterIndex = [...playerDrawerOptions.children].indexOf(playerOption);
        playerPosition = [0, 18, 46, 76][chapterIndex] || 0;
        updatePlayerTimeline();
      }
      if (optionType === "playback-video") {
        document.querySelector("#player-quality").textContent =
          value.startsWith("HEVC") ? "4K HDR" : "1080p";
      }
    }
  }

  const delayReset = event.target.closest("[data-delay-reset]");
  if (delayReset) {
    const settingKey = delayReset.dataset.delayReset;
    playbackDelayState[settingKey] = { value: 0, min: -5000, max: 5000 };
    updateDelayControl(settingKey);
    showToast(`${settingKey === "audioDelay" ? "Audio" : "Subtitle"} delay reset`);
  }

  if (event.target.closest("[data-delay-bound]")) {
    showToast("Use Left or Right to adjust the limit");
  }

  const serverChoice = event.target.closest("[data-server]");
  if (serverChoice) {
    if (serverChoice.dataset.server === "Studio") {
      showView("connection-error");
    } else {
      useServerDefaultBackdrop(serverChoice.dataset.serverUrl || "");
      profileReturnView = "connect";
      showView("profiles");
    }
  }

  const retryServer = event.target.closest("[data-retry-server]");
  if (retryServer && !retryServer.classList.contains("is-loading")) {
    const retryIcon = retryServer.querySelector(".material-symbols-rounded");
    const retryLabel = retryServer.querySelector("span:last-child");
    retryServer.classList.add("is-loading");
    retryIcon.textContent = "progress_activity";
    retryLabel.textContent = "Checking";
    setTimeout(() => {
      retryServer.classList.remove("is-loading");
      retryIcon.textContent = "refresh";
      retryLabel.textContent = "Try again";
      rotateConnectionFailureMessage();
    }, 800);
  }

  const profile = event.target.closest("[data-profile]");
  if (profile) showView("home");

  if (event.target.closest("[data-manual-login]")) showView("login");

  if (event.target.closest("[data-forgot-password]")) showView("recovery");

  const quickConnect = event.target.closest("[data-quick-connect]");
  if (quickConnect) refreshQuickConnect(quickConnect);

  if (event.target.closest(".login-submit")) showView("home");

  if (event.target.closest(".recovery-submit")) {
    document.querySelector(".recovery-result").hidden = false;
    const recoveryButton = event.target.closest(".recovery-submit");
    recoveryButton.classList.add("is-complete");
    recoveryButton.querySelector("span:first-child").textContent = "Request sent";
    recoveryButton.querySelector(".material-symbols-rounded").textContent = "check";
  }

  const passwordReveal = event.target.closest(".login-field__reveal");
  if (passwordReveal) {
    const passwordInput = document.querySelector("#login-password");
    const showPassword = passwordInput.type === "password";
    passwordInput.type = showPassword ? "text" : "password";
    passwordReveal.setAttribute("aria-label", showPassword ? "Hide password" : "Show password");
    passwordReveal.querySelector(".material-symbols-rounded").textContent = showPassword ? "visibility_off" : "visibility";
  }

  if (event.target.closest("[data-connect-manual]")) {
    const address = document.querySelector("#server-address").value.trim();
    if (address) useServerDefaultBackdrop(address);
    showToast(address ? `Connecting to ${normalizeServerAddress(address)}` : "Enter a server address");
  }

  if (event.target.closest(".server-refresh")) {
    showToast("Searching for servers");
  }

  if (event.target.closest("[data-close-detail]")) closeOverlays(true);

  const tab = event.target.closest("[data-settings-tab]");
  if (tab) {
    document.querySelectorAll("[data-settings-tab]").forEach((item) => item.classList.remove("is-active"));
    tab.classList.add("is-active");
    renderSettings(tab.dataset.settingsTab);
  }

  const toggle = event.target.closest(".toggle");
  if (toggle) toggle.classList.toggle("is-on");
  if (event.target.closest("[data-demo-toast]")) showToast("Library refreshed");

  const state = event.target.closest("[data-state]")?.dataset.state;
  if (state) showState(state);

  if (event.target.closest(".state-message .button")) closeOverlays(true);
});

document.addEventListener("change", (event) => {
  if (event.target === detailSeasonSelect) {
    renderSeasonEpisodes();
  }
  if (event.target.matches(".detail-select select")) {
    showToast(`${event.target.value} selected`);
  }
});

document.addEventListener("keydown", (event) => {
  const directions = { ArrowLeft: "left", ArrowRight: "right", ArrowUp: "up", ArrowDown: "down" };
  const playerInFlight =
    !playerLayer.hidden &&
    playerDrawer.hidden &&
    postPlay.hidden &&
    playerRecovery.hidden &&
    playerCheckin.hidden;
  const osdHidden = playerLayer.classList.contains("is-osd-hidden");

  if (playerInFlight && !osdHidden && event.key === "ArrowUp") {
    event.preventDefault();
    const timelineVisible = getComputedStyle(playerTimeline).display !== "none";
    if (timelineVisible && document.activeElement !== playerTimeline) {
      focusElement(playerTimeline);
    } else {
      setPlayerOsdVisible(false);
    }
    return;
  }

  if (playerInFlight && osdHidden) {
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      seekPlayerSeconds(event.key === "ArrowLeft" ? -10 : 10);
      showPlayerMiniSeek();
      return;
    }
    if (event.key === "ArrowDown" || event.key === "Enter") {
      event.preventDefault();
      setPlayerOsdVisible(true, event.key === "ArrowDown" ? "timeline" : "controls");
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      return;
    }
  }

  if (directions[event.key]) {
    event.preventDefault();
    moveFocus(directions[event.key]);
  } else if (event.key === "Enter" && document.activeElement?.matches?.("[data-focus]")) {
    event.preventDefault();
    document.activeElement.click();
  } else if (event.key === "Escape" || event.key === "Backspace") {
    if (document.activeElement?.matches?.(".key") && event.key === "Backspace") return;
    event.preventDefault();
    if (!backPlayerDrawer() && !closeTrackActions(true) && !closeAudioPlayerContext(true) && !closeAudioPlayer() && !closePostPlayEpisodes(true) && !closePostPlay(true) && !stopPlayerCheckin() && !closePlayerRecovery(true) && !closePlayer() && !closePerson(true) && !closeOverlays(true)) {
      const compactStateReturn = {
        splash: "connect",
        "session-expired": "profiles",
        "account-locked": "profiles",
        "certificate-warning": "connect",
        "version-warning": "connect",
      };
      if (activeView === "home" && app.classList.contains("is-media-browsing")) setHomeBrowsing(false, true);
      else if (compactStateReturn[activeView]) showView(compactStateReturn[activeView]);
      else if (activeView === "recovery") showView("login");
      else if (activeView === "login") showView("profiles");
      else if (activeView === "connection-error") showView("connect");
      else if (activeView === "profiles") showView(profileReturnView);
      else if (activeView !== "home" && activeView !== "connect") showView("home");
    }
  }
});

searchInput.addEventListener("input", renderSearchResults);

function renderLibraryNavigation() {
  const buttons = userLibraries.map((library) => {
    const button = document.createElement("button");
    button.className = "top-nav__item";
    button.dataset.focus = "";
    button.dataset.nav = library.view || "library";
    if (!library.view) button.dataset.library = library.id;
    button.innerHTML = `
      <span class="material-symbols-rounded">${library.icon}</span>
      <span>${library.name}</span>
    `;
    return button;
  });
  userLibraryNav.replaceChildren(...buttons);
  libraryPageTitle.textContent = userLibraries.find((library) => library.id === activeLibraryId)?.name || "Library";
}

renderLibraryNavigation();
populateMedia();
renderLiveGuide();
renderSettings();
updateClock();
setInterval(updateClock, 30000);
const requestedScreen = new URLSearchParams(window.location.search).get("screen");
const requestedView = requestedScreen && document.querySelector(`[data-view="${requestedScreen}"]`);

if (
  requestedScreen === "detail" ||
  requestedScreen === "show" ||
  requestedScreen === "album" ||
  requestedScreen === "artist" ||
  requestedScreen === "playlist" ||
  requestedScreen === "audio" ||
  requestedScreen === "player" ||
  requestedScreen === "postplay" ||
  requestedScreen === "playback-error" ||
  requestedScreen === "still-watching" ||
  requestedScreen === "person"
) {
  setTimeout(() => {
    showView(["album", "artist", "playlist", "audio"].includes(requestedScreen) ? "music" : "home");
    if (["album", "artist", "playlist", "audio"].includes(requestedScreen)) {
      const musicItem = document.querySelector(
        requestedScreen === "artist"
          ? '[data-view="music"] [data-title="Marin Vale"]'
          : requestedScreen === "playlist"
            ? '[data-view="music"] [data-title="Open Frequency"]'
          : '[data-view="music"] [data-title="Blue Hours"]',
      );
      setCurrentMedia(musicItem);
    } else if (
      requestedScreen === "show" ||
      requestedScreen === "player" ||
      requestedScreen === "postplay" ||
      requestedScreen === "playback-error" ||
      requestedScreen === "still-watching" ||
      requestedScreen === "person"
    ) {
      const show = mediaCatalog.find((item) => item.type === "Series");
      currentMedia = { ...show, kind: "Series" };
    }
    setTimeout(() => {
      openDetails();
      if (
        requestedScreen === "player" ||
        requestedScreen === "audio" ||
        requestedScreen === "postplay" ||
        requestedScreen === "playback-error" ||
        requestedScreen === "still-watching"
      ) {
        openPlayer();
      }
      if (requestedScreen === "postplay") openPostPlay();
      if (requestedScreen === "playback-error") openPlayerRecovery();
      if (requestedScreen === "still-watching") openPlayerCheckin();
      if (requestedScreen === "person") {
        openPerson(detailLayer.querySelector("[data-person]"));
      }
    }, 80);
  }, 0);
} else if (requestedView) {
  setTimeout(() => showView(requestedScreen), 0);
} else {
  setTimeout(() => {
    const first = document.querySelector('[data-view="connect"] [data-default-focus]');
    first.classList.add("is-key-focused");
    first.focus({ preventScroll: true });
    content.scrollTop = 0;
  }, 80);
}
