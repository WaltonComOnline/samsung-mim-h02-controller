package io.github.waltoncomonline.samsungmimh02.protocol

enum class AcOperation(val wireValue: String, val label: String) {
    Auto("Auto", "Auto"),
    Cool("Cool", "Cool"),
    Heat("Heat", "Heat"),
    Dry("Dry", "Dry"),
    FanOnly("Wind", "Fan"),
    ;

    companion object {
        fun fromWire(value: String?): AcOperation? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class AcFanSpeed(val wireValue: String, val label: String) {
    Auto("Auto", "Auto"),
    Low("Low", "Low"),
    Mid("Mid", "Medium"),
    High("High", "High"),
    Turbo("Turbo", "Turbo"),
    ;

    companion object {
        fun fromWire(value: String?): AcFanSpeed? = entries.firstOrNull { it.wireValue == value }
    }
}

data class AcState(
    val powerOn: Boolean?,
    val operation: AcOperation?,
    val currentTemperatureC: Double?,
    val targetTemperatureC: Int?,
    val fanSpeed: AcFanSpeed?,
    val swingOn: Boolean?,
)

sealed interface AcCommand {
    val attribute: String
    val wireValue: String

    data class Power(val on: Boolean) : AcCommand {
        override val attribute = "AC_FUN_POWER"
        override val wireValue = if (on) "On" else "Off"
    }

    data class Operation(val operation: AcOperation) : AcCommand {
        override val attribute = "AC_FUN_OPMODE"
        override val wireValue = operation.wireValue
    }

    data class TargetTemperature(val celsius: Int) : AcCommand {
        init {
            require(celsius in MIN_TARGET_C..MAX_TARGET_C) { "Target temperature is out of range" }
        }

        override val attribute = "AC_FUN_TEMPSET"
        override val wireValue = celsius.toString()
    }

    data class FanSpeed(val speed: AcFanSpeed) : AcCommand {
        override val attribute = "AC_FUN_WINDLEVEL"
        override val wireValue = speed.wireValue
    }

    data class Swing(val on: Boolean) : AcCommand {
        override val attribute = "AC_FUN_FAN"
        override val wireValue = if (on) "On" else "Off"
    }

    companion object {
        const val MIN_TARGET_C = 16
        const val MAX_TARGET_C = 30
    }
}
