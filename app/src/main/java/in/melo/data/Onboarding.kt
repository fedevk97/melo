package `in`.melo.data

import android.content.Context

/** A control on screen that a step of the first-run tour points at. */
enum class TourTarget { SPEED_DIAL, HOME_MENU, HISTORY, PROFILE, LIBRARY_TAB }

/**
 * The first-run screens in order: the analytics question, the welcome with its choice of look,
 * then a card per part of the app, each pointing at the control it is about. [onLibrary] puts the
 * Library tab behind the card instead of Home.
 */
enum class OnboardingStep(val onLibrary: Boolean = false, val target: TourTarget? = null) {
    CONSENT,
    WELCOME,
    SPEED_DIAL(target = TourTarget.SPEED_DIAL),
    PINS(target = TourTarget.SPEED_DIAL),
    HOME_MENU(target = TourTarget.HOME_MENU),
    HISTORY(target = TourTarget.HISTORY),
    PROFILE(target = TourTarget.PROFILE),
    LIBRARY(onLibrary = true, target = TourTarget.LIBRARY_TAB),
    FINISH;

    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    /** One step back, but never back past the welcome: the analytics answer is given once. */
    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)?.takeIf { it.ordinal > WELCOME.ordinal }

    /** This step's place among the tour's own steps, from 1; the question and the welcome aren't counted. */
    val number: Int get() = ordinal - WELCOME.ordinal

    companion object {
        /** How many steps the tour has, the closing card included. */
        val count: Int = entries.size - 1 - WELCOME.ordinal
    }
}

/** Whether the first-run screens have been through, kept apart from the settings so a backup can't bring them back. */
object Onboarding {
    private const val PREFS = "melo_onboarding"
    private const val SEEN = "seen"

    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
    }
}
