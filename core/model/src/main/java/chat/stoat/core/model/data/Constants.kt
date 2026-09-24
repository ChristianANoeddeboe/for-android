package chat.stoat.core.model.data

// Instance-specific endpoints resolve against the active instance at call time,
// so switching instance takes effect without touching call sites.
val STOAT_BASE: String get() = StoatInstances.current.api
val STOAT_FILES: String get() = StoatInstances.current.files
val STOAT_PROXY: String get() = StoatInstances.current.proxy
val STOAT_WEB_APP: String get() = StoatInstances.current.webApp
val STOAT_WEBSOCKET: String get() = StoatInstances.current.websocket
val STOAT_INVITES: String
    get() = if (StoatInstances.isOfficial) OFFICIAL_INVITES else "${StoatInstances.current.webApp}/invite"

// Official Stoat services, not provided by self-hosted instances.
const val STOAT_SUPPORT = "https://support.stoat.chat"
const val STOAT_MARKETING = "https://stoat.chat"
const val STOAT_BETA_WEB_APP = "https://beta.stoat.chat"
const val STOAT_CHANGELOG = "https://changelog.stoat.chat"

private const val OFFICIAL_INVITES = "https://stt.gg"
