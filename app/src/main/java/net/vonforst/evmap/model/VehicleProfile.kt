package net.vonforst.evmap.model

/**
 * Represents an EV vehicle profile with battery and efficiency specs.
 * Used for range calculation and station filtering.
 */
data class VehicleProfile(
    val id: String,
    val name: String,
    val manufacturer: String,
    val batteryCapacityKwh: Double,
    val officialRangeKm: Double,
    val efficiencyKwhPer100Km: Double  // derived: batteryCapacity / officialRange * 100
) {
    companion object {
        /**
         * Database of popular Indian EV models with real-world specs.
         * Efficiency is estimated for Indian driving conditions
         * (city traffic, heat, AC usage).
         */
        val INDIAN_EVS = listOf(
            // Tata Motors
            VehicleProfile(
                id = "tata_nexon_lr",
                name = "Nexon EV Max (Long Range)",
                manufacturer = "Tata",
                batteryCapacityKwh = 40.5,
                officialRangeKm = 437.0,
                efficiencyKwhPer100Km = 12.5
            ),
            VehicleProfile(
                id = "tata_nexon_sr",
                name = "Nexon EV (Standard Range)",
                manufacturer = "Tata",
                batteryCapacityKwh = 30.2,
                officialRangeKm = 312.0,
                efficiencyKwhPer100Km = 13.0
            ),
            VehicleProfile(
                id = "tata_punch_ev",
                name = "Punch EV",
                manufacturer = "Tata",
                batteryCapacityKwh = 35.0,
                officialRangeKm = 421.0,
                efficiencyKwhPer100Km = 11.5
            ),
            VehicleProfile(
                id = "tata_tiago_ev",
                name = "Tiago EV",
                manufacturer = "Tata",
                batteryCapacityKwh = 24.0,
                officialRangeKm = 315.0,
                efficiencyKwhPer100Km = 10.5
            ),
            VehicleProfile(
                id = "tata_tigor_ev",
                name = "Tigor EV",
                manufacturer = "Tata",
                batteryCapacityKwh = 26.0,
                officialRangeKm = 306.0,
                efficiencyKwhPer100Km = 11.5
            ),
            VehicleProfile(
                id = "tata_curvv_ev",
                name = "Curvv EV",
                manufacturer = "Tata",
                batteryCapacityKwh = 55.0,
                officialRangeKm = 585.0,
                efficiencyKwhPer100Km = 13.0
            ),

            // MG Motor
            VehicleProfile(
                id = "mg_zs_ev",
                name = "ZS EV",
                manufacturer = "MG",
                batteryCapacityKwh = 50.3,
                officialRangeKm = 461.0,
                efficiencyKwhPer100Km = 14.5
            ),
            VehicleProfile(
                id = "mg_comet",
                name = "Comet EV",
                manufacturer = "MG",
                batteryCapacityKwh = 17.3,
                officialRangeKm = 230.0,
                efficiencyKwhPer100Km = 10.0
            ),
            VehicleProfile(
                id = "mg_windsor",
                name = "Windsor EV",
                manufacturer = "MG",
                batteryCapacityKwh = 38.0,
                officialRangeKm = 331.0,
                efficiencyKwhPer100Km = 15.0
            ),

            // Mahindra
            VehicleProfile(
                id = "mahindra_xuv400",
                name = "XUV400",
                manufacturer = "Mahindra",
                batteryCapacityKwh = 39.4,
                officialRangeKm = 456.0,
                efficiencyKwhPer100Km = 12.0
            ),
            VehicleProfile(
                id = "mahindra_be6",
                name = "BE 6",
                manufacturer = "Mahindra",
                batteryCapacityKwh = 59.0,
                officialRangeKm = 535.0,
                efficiencyKwhPer100Km = 15.0
            ),

            // Hyundai
            VehicleProfile(
                id = "hyundai_ioniq5",
                name = "Ioniq 5",
                manufacturer = "Hyundai",
                batteryCapacityKwh = 72.6,
                officialRangeKm = 631.0,
                efficiencyKwhPer100Km = 16.8
            ),
            VehicleProfile(
                id = "hyundai_creta_ev",
                name = "Creta EV",
                manufacturer = "Hyundai",
                batteryCapacityKwh = 51.4,
                officialRangeKm = 473.0,
                efficiencyKwhPer100Km = 14.5
            ),

            // Kia
            VehicleProfile(
                id = "kia_ev6",
                name = "EV6",
                manufacturer = "Kia",
                batteryCapacityKwh = 77.4,
                officialRangeKm = 528.0,
                efficiencyKwhPer100Km = 18.0
            ),

            // BYD
            VehicleProfile(
                id = "byd_atto3",
                name = "Atto 3",
                manufacturer = "BYD",
                batteryCapacityKwh = 60.48,
                officialRangeKm = 521.0,
                efficiencyKwhPer100Km = 15.5
            ),
            VehicleProfile(
                id = "byd_seal",
                name = "Seal",
                manufacturer = "BYD",
                batteryCapacityKwh = 82.56,
                officialRangeKm = 650.0,
                efficiencyKwhPer100Km = 17.0
            ),
            VehicleProfile(
                id = "byd_e6",
                name = "e6",
                manufacturer = "BYD",
                batteryCapacityKwh = 71.7,
                officialRangeKm = 415.0,
                efficiencyKwhPer100Km = 22.0
            ),

            // Citroen
            VehicleProfile(
                id = "citroen_ec3",
                name = "eC3",
                manufacturer = "Citroen",
                batteryCapacityKwh = 29.2,
                officialRangeKm = 320.0,
                efficiencyKwhPer100Km = 12.5
            ),

            // BMW
            VehicleProfile(
                id = "bmw_ix1",
                name = "iX1",
                manufacturer = "BMW",
                batteryCapacityKwh = 66.5,
                officialRangeKm = 440.0,
                efficiencyKwhPer100Km = 19.5
            ),

            // Mercedes
            VehicleProfile(
                id = "merc_eqa",
                name = "EQA",
                manufacturer = "Mercedes",
                batteryCapacityKwh = 66.5,
                officialRangeKm = 426.0,
                efficiencyKwhPer100Km = 20.5
            ),

            // Volvo
            VehicleProfile(
                id = "volvo_xc40_recharge",
                name = "XC40 Recharge",
                manufacturer = "Volvo",
                batteryCapacityKwh = 78.0,
                officialRangeKm = 418.0,
                efficiencyKwhPer100Km = 23.0
            ),

            // Audi
            VehicleProfile(
                id = "audi_etron",
                name = "e-tron",
                manufacturer = "Audi",
                batteryCapacityKwh = 95.0,
                officialRangeKm = 484.0,
                efficiencyKwhPer100Km = 24.0
            ),

            // OLA
            VehicleProfile(
                id = "ola_s1_pro",
                name = "S1 Pro (Scooter)",
                manufacturer = "OLA",
                batteryCapacityKwh = 3.97,
                officialRangeKm = 170.0,
                efficiencyKwhPer100Km = 3.2
            ),

            // Ather
            VehicleProfile(
                id = "ather_450x",
                name = "450X (Scooter)",
                manufacturer = "Ather",
                batteryCapacityKwh = 3.7,
                officialRangeKm = 150.0,
                efficiencyKwhPer100Km = 3.4
            )
        )

        fun findById(id: String): VehicleProfile? = INDIAN_EVS.find { it.id == id }

        /** Group vehicles by manufacturer for display */
        fun groupedByManufacturer(): Map<String, List<VehicleProfile>> =
            INDIAN_EVS.groupBy { it.manufacturer }
    }
}
