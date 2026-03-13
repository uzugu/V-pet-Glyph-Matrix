package com.digimon.glyph.emulator

object EvolutionGuideRepository {

    private val perfectBattleRoute = listOf(
        "15 Child-stage battles",
        "12 to 15 wins in the most recent 15 Child-stage battles",
        "15 Adult-stage battles",
        "12 to 15 wins in the most recent 15 Adult-stage battles"
    )

    private const val perfectBattleNote =
        "Humulos high-chance route. Perfect / Ultimate can still happen with less, but these are the strong target numbers."

    fun getGuide(versionCode: String): EvolutionVersionGuide {
        return when (versionCode.uppercase()) {
            "V2" -> v2Guide
            "V3" -> v3Guide
            else -> v1Guide
        }
    }

    private val v1Guide = EvolutionVersionGuide(
        code = "V1",
        title = "DIGITAL MONSTER V1",
        intro = "Classic monster flow. Child and Adult routes split on care mistakes, training, overfeeding, sleep disturbance, and battle history.",
        rules = listOf(
            "Set the clock first, then wait about 5 to 10 minutes for the egg to hatch.",
            "Child to Adult checks mostly care mistakes, training, overfeeds, and sleep disturbances.",
            "Ultimate checks care strongly, but the recent stage battle window matters more than the simple lifetime win percentage.",
            "Ultimate outcomes are not guaranteed, even with a strong run."
        ),
        profiles = mapOf(
            1 to EvolutionProfile(1, "Opening hatch stage.", evolvesTo = listOf(
                EvolutionRoute(2, "Normal hatch", listOf("Wait for the egg to open"))
            )),
            2 to EvolutionProfile(2, "First care split.", evolvesFrom = listOf("Botamon"), evolvesTo = listOf(
                EvolutionRoute(3, "Clean early care", listOf("0 to 3 care mistakes")),
                EvolutionRoute(4, "Rough early care", listOf("4 or more care mistakes"))
            )),
            3 to EvolutionProfile(3, "Balanced rookie with several strong branches.", evolvesFrom = listOf("Koromon"), evolvesTo = listOf(
                EvolutionRoute(5, "Greymon route", listOf("0 to 3 care mistakes", "32 or more training"), "Primary strong-care route"),
                EvolutionRoute(6, "Tyranomon route", listOf("4 or more care mistakes", "5 to 15 training", "3 or more overfeeds", "0 to 4 sleep disturbances")),
                EvolutionRoute(7, "Devimon route", listOf("0 to 3 care mistakes", "0 to 31 training")),
                EvolutionRoute(8, "Meramon route", listOf("4 or more care mistakes", "16 or more training", "3 or more overfeeds", "0 to 6 sleep disturbances")),
                EvolutionRoute(11, "Numemon route", listOf("4 or more care mistakes", "0 to 4 training or 0 to 2 overfeeds or 7 or more sleep disturbances"))
            )),
            4 to EvolutionProfile(4, "Alternative rookie with air, sea, and dark branches.", evolvesFrom = listOf("Koromon"), evolvesTo = listOf(
                EvolutionRoute(7, "Devimon route", listOf("0 to 3 care mistakes", "48 or more training")),
                EvolutionRoute(8, "Meramon route", listOf("0 to 3 care mistakes", "0 to 47 training")),
                EvolutionRoute(9, "Airdramon route", listOf("4 or more care mistakes", "8 to 31 training", "0 to 3 overfeeds", "9 or more sleep disturbances")),
                EvolutionRoute(10, "Seadramon route", listOf("4 or more care mistakes", "8 to 31 training", "4 or more overfeeds", "0 to 8 sleep disturbances")),
                EvolutionRoute(11, "Numemon route", listOf("4 or more care mistakes", "0 to 7 training or 32 or more training"))
            )),
            5 to EvolutionProfile(5, "Strong Adult line.", evolvesFrom = listOf("Agumon"), evolvesTo = listOf(
                EvolutionRoute(12, "MetalGreymon route", perfectBattleRoute, perfectBattleNote)
            )),
            6 to EvolutionProfile(6, "Adult branch that favors a Mamemon finish.", evolvesFrom = listOf("Agumon"), evolvesTo = listOf(
                EvolutionRoute(13, "Mamemon route", perfectBattleRoute, perfectBattleNote)
            )),
            7 to EvolutionProfile(7, "Dark Adult branch.", evolvesFrom = listOf("Agumon", "Betamon"), evolvesTo = listOf(
                EvolutionRoute(12, "MetalGreymon route", perfectBattleRoute, perfectBattleNote)
            )),
            8 to EvolutionProfile(8, "Fire Adult branch.", evolvesFrom = listOf("Agumon", "Betamon"), evolvesTo = listOf(
                EvolutionRoute(13, "Mamemon route", perfectBattleRoute, perfectBattleNote)
            )),
            9 to EvolutionProfile(9, "Air Adult branch.", evolvesFrom = listOf("Betamon"), evolvesTo = listOf(
                EvolutionRoute(12, "MetalGreymon route", perfectBattleRoute, perfectBattleNote)
            )),
            10 to EvolutionProfile(10, "Sea Adult branch.", evolvesFrom = listOf("Betamon"), evolvesTo = listOf(
                EvolutionRoute(13, "Mamemon route", perfectBattleRoute, perfectBattleNote)
            )),
            11 to EvolutionProfile(11, "Failure Adult. Still has a rescue route.", evolvesFrom = listOf("Agumon", "Betamon"), evolvesTo = listOf(
                EvolutionRoute(14, "Monzaemon route", perfectBattleRoute, perfectBattleNote)
            )),
            12 to EvolutionProfile(12, "Top stage. Outcome chance still applies.", evolvesFrom = listOf("Greymon", "Devimon", "Airdramon")),
            13 to EvolutionProfile(13, "Top stage. Outcome chance still applies.", evolvesFrom = listOf("Tyranomon", "Meramon", "Seadramon")),
            14 to EvolutionProfile(14, "Top stage reached through the Numemon recovery route.", evolvesFrom = listOf("Numemon"))
        )
    )

    private val v2Guide = EvolutionVersionGuide(
        code = "V2",
        title = "DIGITAL MONSTER V2",
        intro = "Second roster with Gabumon and Elecmon as the main rookies. Branch logic stays close to V1 but the destination forms are different.",
        rules = listOf(
            "Set the clock first, then wait about 5 to 10 minutes for the egg to hatch.",
            "Rookie branches still split mostly on care mistakes, training, overfeeding, and sleep disturbance.",
            "Perfect checks depend on battle performance across the active stage windows, not just the simple displayed win rate.",
            "Top-stage outcomes still include a chance component."
        ),
        profiles = mapOf(
            1 to EvolutionProfile(1, "Opening hatch stage.", evolvesTo = listOf(
                EvolutionRoute(2, "Normal hatch", listOf("Wait for the egg to open"))
            )),
            2 to EvolutionProfile(2, "Early care split.", evolvesFrom = listOf("Punimon"), evolvesTo = listOf(
                EvolutionRoute(3, "Gabumon route", listOf("0 to 3 care mistakes")),
                EvolutionRoute(4, "Elecmon route", listOf("4 or more care mistakes"))
            )),
            3 to EvolutionProfile(3, "Rookie with disciplined and wild battle routes.", evolvesFrom = listOf("Tsunomon"), evolvesTo = listOf(
                EvolutionRoute(5, "Kabuterimon route", listOf("0 to 3 care mistakes", "48 or more training")),
                EvolutionRoute(6, "Garurumon route", listOf("4 or more care mistakes", "5 to 31 training", "3 or more overfeeds", "0 to 4 sleep disturbances")),
                EvolutionRoute(7, "Angemon route", listOf("0 to 3 care mistakes", "0 to 47 training")),
                EvolutionRoute(8, "Frigimon route", listOf("4 or more care mistakes", "32 or more training", "3 or more overfeeds", "0 to 6 sleep disturbances")),
                EvolutionRoute(11, "Vegiemon route", listOf("4 or more care mistakes", "0 to 4 training or 0 to 2 overfeeds or 7 or more sleep disturbances"))
            )),
            4 to EvolutionProfile(4, "Alternative rookie with bird, whale, and failure branches.", evolvesFrom = listOf("Tsunomon"), evolvesTo = listOf(
                EvolutionRoute(7, "Angemon route", listOf("0 to 3 care mistakes", "48 or more training")),
                EvolutionRoute(9, "Birdramon route", listOf("0 to 3 care mistakes", "0 to 47 training")),
                EvolutionRoute(8, "Frigimon route", listOf("4 or more care mistakes", "8 to 31 training", "0 to 5 overfeeds", "5 or more sleep disturbances")),
                EvolutionRoute(10, "Whamon route", listOf("4 or more care mistakes", "32 or more training", "6 or more overfeeds", "0 to 4 sleep disturbances")),
                EvolutionRoute(11, "Vegiemon route", listOf("4 or more care mistakes", "0 to 7 training"))
            )),
            5 to EvolutionProfile(5, "Strong Adult route.", evolvesFrom = listOf("Gabumon"), evolvesTo = listOf(
                EvolutionRoute(12, "SkullGreymon route", perfectBattleRoute, perfectBattleNote)
            )),
            6 to EvolutionProfile(6, "Balanced Adult route.", evolvesFrom = listOf("Gabumon"), evolvesTo = listOf(
                EvolutionRoute(13, "MetalMamemon route", perfectBattleRoute, perfectBattleNote)
            )),
            7 to EvolutionProfile(7, "Holy Adult route.", evolvesFrom = listOf("Gabumon", "Elecmon"), evolvesTo = listOf(
                EvolutionRoute(12, "SkullGreymon route", perfectBattleRoute, perfectBattleNote)
            )),
            8 to EvolutionProfile(8, "Cold Adult route.", evolvesFrom = listOf("Gabumon", "Elecmon"), evolvesTo = listOf(
                EvolutionRoute(13, "MetalMamemon route", perfectBattleRoute, perfectBattleNote)
            )),
            9 to EvolutionProfile(9, "Bird Adult route.", evolvesFrom = listOf("Elecmon"), evolvesTo = listOf(
                EvolutionRoute(12, "SkullGreymon route", perfectBattleRoute, perfectBattleNote)
            )),
            10 to EvolutionProfile(10, "Whale Adult route.", evolvesFrom = listOf("Elecmon"), evolvesTo = listOf(
                EvolutionRoute(13, "MetalMamemon route", perfectBattleRoute, perfectBattleNote)
            )),
            11 to EvolutionProfile(11, "Failure Adult with its own top-stage outcome.", evolvesFrom = listOf("Gabumon", "Elecmon"), evolvesTo = listOf(
                EvolutionRoute(14, "Vademon route", perfectBattleRoute, perfectBattleNote)
            )),
            12 to EvolutionProfile(12, "Top stage. Outcome chance still applies.", evolvesFrom = listOf("Kabuterimon", "Angemon", "Birdramon")),
            13 to EvolutionProfile(13, "Top stage. Outcome chance still applies.", evolvesFrom = listOf("Garurumon", "Frigimon", "Whamon")),
            14 to EvolutionProfile(14, "Top stage reached from the Vegiemon branch.", evolvesFrom = listOf("Vegiemon"))
        )
    )

    private val v3Guide = EvolutionVersionGuide(
        code = "V3",
        title = "DIGITAL MONSTER V3",
        intro = "Patamon and Kunemon lead this roster. The same classic care logic applies, but the adult and top-stage outcomes are unique to V3.",
        rules = listOf(
            "Set the clock first, then wait about 5 to 10 minutes for the egg to hatch.",
            "Rookie to Adult checks still pivot on care mistakes, training, overfeeding, and sleep disturbance.",
            "Ultimate checks care and battle windows together. The displayed win percent is only a clue, not the whole rule.",
            "Top-stage outcomes still have a chance element."
        ),
        profiles = mapOf(
            1 to EvolutionProfile(1, "Opening hatch stage.", evolvesTo = listOf(
                EvolutionRoute(2, "Normal hatch", listOf("Wait for the egg to open"))
            )),
            2 to EvolutionProfile(2, "Early care split.", evolvesFrom = listOf("Poyomon"), evolvesTo = listOf(
                EvolutionRoute(3, "Patamon route", listOf("0 to 3 care mistakes")),
                EvolutionRoute(4, "Kunemon route", listOf("4 or more care mistakes"))
            )),
            3 to EvolutionProfile(3, "Rookie with balanced, dark, and failure branches.", evolvesFrom = listOf("Tokomon"), evolvesTo = listOf(
                EvolutionRoute(5, "Unimon route", listOf("0 to 3 care mistakes", "48 or more training")),
                EvolutionRoute(6, "Centarumon route", listOf("4 or more care mistakes", "5 to 31 training", "3 or more overfeeds", "0 to 4 sleep disturbances")),
                EvolutionRoute(7, "Ogremon route", listOf("0 to 3 care mistakes", "0 to 47 training")),
                EvolutionRoute(8, "Bakemon route", listOf("4 or more care mistakes", "32 or more training", "3 or more overfeeds", "0 to 6 sleep disturbances")),
                EvolutionRoute(11, "Scumon route", listOf("4 or more care mistakes", "0 to 4 training or 0 to 2 overfeeds or 7 or more sleep disturbances"))
            )),
            4 to EvolutionProfile(4, "Alternative rookie with shell and drill branches.", evolvesFrom = listOf("Tokomon"), evolvesTo = listOf(
                EvolutionRoute(7, "Ogremon route", listOf("0 to 3 care mistakes", "48 or more training")),
                EvolutionRoute(9, "Shellmon route", listOf("0 to 3 care mistakes", "0 to 47 training")),
                EvolutionRoute(8, "Bakemon route", listOf("4 or more care mistakes", "8 to 31 training", "0 to 5 overfeeds", "5 or more sleep disturbances")),
                EvolutionRoute(10, "Drimogemon route", listOf("4 or more care mistakes", "32 or more training", "6 or more overfeeds", "0 to 4 sleep disturbances")),
                EvolutionRoute(11, "Scumon route", listOf("4 or more care mistakes", "0 to 7 training"))
            )),
            5 to EvolutionProfile(5, "Strong Adult route.", evolvesFrom = listOf("Patamon"), evolvesTo = listOf(
                EvolutionRoute(12, "Andromon route", perfectBattleRoute, perfectBattleNote)
            )),
            6 to EvolutionProfile(6, "Centaur Adult route.", evolvesFrom = listOf("Patamon"), evolvesTo = listOf(
                EvolutionRoute(13, "Giromon route", perfectBattleRoute, perfectBattleNote)
            )),
            7 to EvolutionProfile(7, "Dark Adult route.", evolvesFrom = listOf("Patamon", "Kunemon"), evolvesTo = listOf(
                EvolutionRoute(12, "Andromon route", perfectBattleRoute, perfectBattleNote)
            )),
            8 to EvolutionProfile(8, "Ghost Adult route.", evolvesFrom = listOf("Patamon", "Kunemon"), evolvesTo = listOf(
                EvolutionRoute(13, "Giromon route", perfectBattleRoute, perfectBattleNote)
            )),
            9 to EvolutionProfile(9, "Shell Adult route.", evolvesFrom = listOf("Kunemon"), evolvesTo = listOf(
                EvolutionRoute(12, "Andromon route", perfectBattleRoute, perfectBattleNote)
            )),
            10 to EvolutionProfile(10, "Drill Adult route.", evolvesFrom = listOf("Kunemon"), evolvesTo = listOf(
                EvolutionRoute(13, "Giromon route", perfectBattleRoute, perfectBattleNote)
            )),
            11 to EvolutionProfile(11, "Failure Adult with its own recovery top stage.", evolvesFrom = listOf("Patamon", "Kunemon"), evolvesTo = listOf(
                EvolutionRoute(14, "Etemon route", perfectBattleRoute, perfectBattleNote)
            )),
            12 to EvolutionProfile(12, "Top stage. Outcome chance still applies.", evolvesFrom = listOf("Unimon", "Ogremon", "Shellmon")),
            13 to EvolutionProfile(13, "Top stage. Outcome chance still applies.", evolvesFrom = listOf("Centarumon", "Bakemon", "Drimogemon")),
            14 to EvolutionProfile(14, "Top stage reached from the Scumon branch.", evolvesFrom = listOf("Scumon"))
        )
    )
}
