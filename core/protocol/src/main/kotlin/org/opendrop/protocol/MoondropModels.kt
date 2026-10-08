package org.opendrop.protocol

enum class ModelKind { EARBUDS, HEADPHONES, SPEAKER }

enum class Chip { BLUETRUM, QUALCOMM, JIELI, AIROHA }

/**
 * A Bluetooth model from MOONDROP Link's product catalogue. [name] is the
 * catalogue's `model` string, which is what the device reports as its GAIA
 * variant name (checked on the Space Travel).
 */
data class MoondropModel(
    val name: String,
    val kind: ModelKind,
    val chip: Chip,
    /** Bands of the on-device user EQ, per the catalogue. */
    val eqBands: Int,
    /** The EQ presets are the Space Travel's three, so [EqPreset] names them. */
    val spaceTravelPresets: Boolean = false,
)

/** Bluetooth models in Link's catalogue (2026-10-08). All are controlled over GAIA. */
object MoondropModels {
    private fun model(name: String, kind: ModelKind, chip: Chip, eqBands: Int, spaceTravelPresets: Boolean = false) =
        MoondropModel(name, kind, chip, eqBands, spaceTravelPresets)

    private const val BANDS = 5

    val all: List<MoondropModel> = listOf(
        model("Moondrop Space Travel", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS, spaceTravelPresets = true),
        model("PANDAER Space Travel 2", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Space Travel 2", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Space Travel 2 Ultra", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Space Force", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP MOON TRAVEL", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop Golden Ages", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop Golden Ages 2", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop Nekocake", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop Nekocake Acht Acht Limited", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop Nekocake QBZ-191", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP MOCA", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Me 163 Komet MOCA", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP Ultrasonic", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP EVO 2", ModelKind.EARBUDS, Chip.BLUETRUM, 10),
        model("Moondrop SUSANOO TWS", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("SUSANOO TWS", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop U.C.T.S.", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP Pill", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Pill Gotoh Hitori", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Pill Ijichi Nijika", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Pill Kita Ikuyo", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Pill Yamada Ryo", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("PANDAER Open Air Pill", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("ZZZ-ANGELS-OWS", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("H.I.D.E.404_Klukai", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("LAPLACE-OBA-Ⅱ", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("PUNISHING:GRAY RAVEN", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("Moondrop PUNISHING;GRAY RAVEN", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("BIANCA:STIGMATA", ModelKind.HEADPHONES, Chip.BLUETRUM, BANDS),
        model("ROBIN'S Earphones", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP x YASUNO KIYONO", ModelKind.EARBUDS, Chip.BLUETRUM, BANDS),
        model("MOONDROP EDGE", ModelKind.HEADPHONES, Chip.BLUETRUM, BANDS),
        model("MOONDROP EDGE 2", ModelKind.HEADPHONES, Chip.BLUETRUM, BANDS),
        model("SINGER HEADPHONE", ModelKind.HEADPHONES, Chip.BLUETRUM, BANDS),
        model("Moondrop SINGER HEADPHONE", ModelKind.HEADPHONES, Chip.BLUETRUM, BANDS),
        model("MOONDROP MM3A", ModelKind.SPEAKER, Chip.BLUETRUM, 8),
        model("Moondrop Alice", ModelKind.EARBUDS, Chip.QUALCOMM, BANDS),
        model("Moondrop Sparks", ModelKind.EARBUDS, Chip.QUALCOMM, BANDS),
        model("MOONDROP Voyager", ModelKind.HEADPHONES, Chip.QUALCOMM, BANDS),
        model("MOONDROP littlewhite", ModelKind.HEADPHONES, Chip.QUALCOMM, BANDS),
        model("MOONDROP MIRAGE", ModelKind.EARBUDS, Chip.JIELI, 10),
        model("MOONDROP Pudding", ModelKind.EARBUDS, Chip.JIELI, 10),
        model("MOONDROP The Garden", ModelKind.EARBUDS, Chip.AIROHA, 10),
    )

    private val byKey = all.associateBy { key(it.name) }

    /** The model with this variant name, ignoring case and surrounding spaces. */
    fun find(variantName: String?): MoondropModel? = variantName?.let { byKey[key(it)] }

    private fun key(name: String) = name.trim().lowercase()
}
