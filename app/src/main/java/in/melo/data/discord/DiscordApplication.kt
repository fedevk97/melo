package `in`.melo.data.discord

/**
 * The Discord application the presence card is published under.
 *
 * A public identifier, not a credential: it is sent with every presence. Discord
 * mirrors cover art onto its CDN only for a registered application, so without
 * it the card has no art. Its name and icon come from the Developer Portal.
 */
object DiscordApplication {
    const val ID = "1573613840254369944"
}
