package com.aeswox.arcplayer.metadata

/**
 * Where a file sitting on this phone belongs, decided from nothing but where it lies and how long it
 * runs.
 *
 * The phone's video table is the camera roll, the messenger's saved clips and the screen recordings
 * someone made last Tuesday, together with the films. Asking the catalogues about all of them spends a
 * request per holiday video to be told it is not a film, and — worse — a name that parses by accident
 * puts a clip on the shelf beside the collection. So the shelf takes only what a person *filed* as a
 * video, which is a question a path answers: nobody's camera writes into `Movies/`, and nobody puts
 * their films in `DCIM/Camera/`.
 *
 * This is a budget on what gets asked, not a rule about what exists. Everything still shows on the
 * device screens, which read the whole table.
 */
object PhoneVideos {

    /**
     * Shortest thing worth calling a film or an episode.
     *
     * Twenty minutes is under the shortest episode of anything people keep in a folder — the odd
     * fifteen-minute anime short aside, and those arrive on the shelf from Telegram anyway, where the
     * name was typed by whoever put it there. Everything a phone produces by itself is shorter.
     */
    const val MIN_RUNTIME_MS = 20L * 60L * 1000L

    /**
     * Folders that hold things a person or an app captured, rather than things they collected.
     *
     * Matched against every segment of the path rather than its leaf, because a messenger hides its
     * videos a whole floor down — `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/` — and a
     * leaf-only rule reads that as a folder called "WhatsApp Video" only when the path happens to end
     * there.
     */
    private val capturedFolders = listOf(
        "camera", "dcim", "whatsapp", "snapchat", "instagram", "messenger", "facebook", "tiktok",
        "screenshot", "screenrecord", "screencast", "recording", "pictures", "motionphoto", "pixel"
    )

    /** Paths that belong to an app's own storage rather than to the person's library. */
    private val privateFolders = listOf("android/data", "android/obb", "cache")

    /**
     * The folder this app files its own keeps in.
     *
     * Excluded by name rather than by luck. A download is a copy of a shelf entry that is already on the
     * shelf, and the shelf reads this table too — so without this the same film arrives twice, once as
     * the Telegram copy with its offline tick and once as a phone video with a card of its own. It is
     * filed under `Movies` precisely so that everything else on the device can find it, which is the
     * one folder rule that would otherwise have let it through.
     */
    private const val KEPT_FOLDER = "arc player"

    /**
     * What to make of one device file.
     *
     * [relativePath] is MediaStore's own, which is always rooted at the volume and ends with a slash —
     * `Movies/`, or `DCIM/Camera/` — and empty for a file the scanner cannot place, which is treated as
     * no evidence rather than as permission.
     */
    fun verdictOf(relativePath: String, durationMs: Long): PhoneVideoVerdict {
        val path = relativePath.lowercase()
        // These are the only two segments of their paths, so they are read against the whole path — a
        // single segment never contains a slash and could never match them.
        if (privateFolders.any { path.contains(it) }) return PhoneVideoVerdict.PERSONAL
        if (path.split('/').any { segment ->
                segment.contains(KEPT_FOLDER) || capturedFolders.any { segment.contains(it) }
            }
        ) {
            return PhoneVideoVerdict.PERSONAL
        }
        // A file MediaStore has no duration for is a file its scanner could not read, and the player is
        // no likelier to open it than the shelf is to name it.
        if (durationMs < MIN_RUNTIME_MS) return PhoneVideoVerdict.CLIP
        return PhoneVideoVerdict.COLLECTION
    }
}

/** Where one file on this phone belongs. */
enum class PhoneVideoVerdict {
    /** Filed among the videos a person keeps: worth a card, and worth a request. */
    COLLECTION,

    /** Too short to be a feature or an episode, whatever folder it happens to sit in. */
    CLIP,

    /** Captured or received by a camera or a messenger, which is not a collection. */
    PERSONAL
}
