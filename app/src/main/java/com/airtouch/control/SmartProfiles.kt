package com.airtouch.control

/** Escolhe sozinho o perfil certo conforme o app em primeiro plano ou a aba aberta. */
object SmartProfiles {
    const val PRESENTATION = "Apresentação"
    const val READING = "Leitura"
    const val NAVIGATION = "Navegação"
    const val GAMES = "Jogos"
    const val MEDIA = "Mídia"

    private val slides = listOf("powerpoint", "presentation", "slides", "impress", "keynote", "officesuite")
    private val reading = listOf("pdf", "reader", "docs", "word", "books", "kindle", "onenote", "document", "drive", "samsung.android.app.notes")
    private val media = listOf("youtube", "netflix", "spotify", "music", "video", "vlc", "player", "primevideo", "disney", "twitch", "podcast")
    private val games = listOf("game", "roblox", "minecraft", "supercell", "king.com", "garena", "epicgames")
    private val launcher = listOf("launcher", "systemui", "settings")

    /** Perfil sugerido para o pacote do app, ou null para manter o perfil escolhido. */
    fun forPackage(pkg: String?): String? {
        val p = pkg?.lowercase() ?: return null
        return when {
            slides.any { it in p } -> PRESENTATION
            media.any { it in p } -> MEDIA
            games.any { it in p } -> GAMES
            reading.any { it in p } -> READING
            launcher.any { it in p } -> NAVIGATION
            else -> null
        }
    }

    /** Perfil sugerido para uma aba do app (pelo nome do enum AppTab). */
    fun forTab(tabName: String): String? = when (tabName) {
        "PRESENT" -> PRESENTATION
        "DOCS" -> READING
        "GAMES" -> GAMES
        else -> null
    }
}
