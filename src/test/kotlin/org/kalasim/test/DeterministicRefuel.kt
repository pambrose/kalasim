package org.kalasim.test

import org.apache.commons.math3.distribution.UniformRealDistribution
import org.kalasim.*
import org.kalasim.misc.printThis
import org.kalasim.monitors.*
import org.kalasim.plot.kravis.display
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * A car arrives at the gas station for refueling.
 *
 * It requests one of the gas station's fuel pumps and tries to get the
 * desired amount of gas from it. If the stations reservoir is
 * depleted, the car has to wait for the tank truck to arrive.
 */
object DeterministicRefuel {

    // based on SimPy example model
    val GAS_STATION_SIZE = 2000.0  // liters
    val THRESHOLD = 25.0  // Threshold for calling the tank truck (in %)
    val FUEL_TANK_SIZE = 50.0  // liters
    val FUEL_TANK_LEVEL =
        UniformRealDistribution(5.0, 25.0).apply { reseedRandomGenerator(1) }// Min/max levels of fuel tanks (in liters)
    val REFUELING_SPEED = 2.0  // liters / second
    val TANK_TRUCK_TIME = 300.0  // Seconds it takes the tank truck to arrive
    val T_INTER = UniformRealDistribution(
        100.0,
        200.0
    ).apply { reseedRandomGenerator(1) }  // Create a car every [min, max] seconds

    //    val SIM_TIME = 200000.0  // Original Simulation time in seconds
    val SIM_TIME = 20000.0  // Simulation time in seconds


    private val FUEL_PUMP = "fuel_pump"

    private val CAR_LEAVING = "car_leaving"

    private val TRUCKS_EN_ROUTE = "trucks_on_the_road"
    private val TRUCKS_ORDERED = "trucks_ordered"

    @JvmStatic
    fun main(args: Array<String>) {

        class GasStation : Resource(capacity = 2.0)

        class TankTruck : Component() {
            val fuelPump: DepletableResource by inject(qualifier = named(FUEL_PUMP))

            override fun process() = sequence {
                hold(TANK_TRUCK_TIME)

                val amount = fuelPump.claimed
                put(fuelPump withQuantity amount)
            }
        }

        // we can either inject by constructor...
        class Car(val gasStation: GasStation) : Component() {

            // ... or we can inject by field which is
            // in particular useful for untypes resources and components

            //            val gasStation : Resource by inject(qualifier = named("gas_station"))
            val fuelPump: DepletableResource by inject(qualifier = named(FUEL_PUMP))

            override fun process() = sequence {
                val fuelTankLevel = FUEL_TANK_LEVEL.sample()

                request(gasStation)

                val litersRequired = FUEL_TANK_SIZE - fuelTankLevel

                // order a new Tank if the fuelpump runs of out fuel
                if ((fuelPump.available - litersRequired) / fuelPump.capacity * 100 < THRESHOLD) {
                    log("running out of fuel at $gasStation. Ordering new fuel truck...")
                    TankTruck()

                    // track number of trucks
                    get<IntTimeline>(named(TRUCKS_ORDERED)).inc()
                    get<IntTimeline>(named(TRUCKS_EN_ROUTE)).addValue(
                        env.queue.count { it is TankTruck }
                    )
                }

                request(fuelPump withQuantity litersRequired)
                hold(litersRequired / REFUELING_SPEED)
                get<IntTimeline>(named(CAR_LEAVING)).inc()
//                get<MetricTimeline>(named(TRUCKS_EN_ROUTE)).inc()
            }
        }

        configureEnvironment(true) {

//            single(qualifier = named("gas_station")) { Resource("gas_station", 2) }

            single { GasStation() }

            single(qualifier = named(FUEL_PUMP)) { DepletableResource(FUEL_PUMP, GAS_STATION_SIZE) }
            single(qualifier = named(CAR_LEAVING)) { IntTimeline(CAR_LEAVING) }
            single(qualifier = named(TRUCKS_EN_ROUTE)) { IntTimeline(TRUCKS_EN_ROUTE) }
            single(qualifier = named(TRUCKS_ORDERED)) { IntTimeline(TRUCKS_ORDERED) }
        }.apply {

            ComponentGenerator(iat = T_INTER) { Car(get()) }

            run(SIM_TIME)

            val fuelPump = get<DepletableResource>(qualifier = named(FUEL_PUMP))

            fuelPump.apply {
                capacityTimeline.printHistogram()
                claimedTimeline.printHistogram()
                availabilityTimeline.printHistogram()
                occupancyTimeline.printHistogram()
            }


            get<GasStation>().requesters.sizeTimeline.printThis()
            get<GasStation>().requesters.lengthOfStayStatistics.printThis()

            // save the simulation state to file
//            Json.encodeToString(this).println()

            get<GasStation>().claimedTimeline.display()
            fuelPump.claimedTimeline.display()
            get<IntTimeline>(named(CAR_LEAVING)).display()
            get<IntTimeline>(named(TRUCKS_EN_ROUTE)).display()
            get<IntTimeline>(named(TRUCKS_ORDERED)).display()
        }
    }
}
