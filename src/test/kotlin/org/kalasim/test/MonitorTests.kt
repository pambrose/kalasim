package org.kalasim.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues
import org.junit.Test
import org.kalasim.misc.merge
import org.kalasim.misc.mergeStats
import org.kalasim.monitors.*
import org.kalasim.test.MonitorTests.Car.*
import org.kalasim.tt

class MonitorTests {

    private enum class Car {
        AUDI, PORSCHE, VW, TOYOTA
    }


    @Test
    fun `Frequency stats should be correct`() = createTestSimulation {
        val m = CategoryMonitor<Car>()
        m.addValue(AUDI)
        m.addValue(AUDI)
        m.addValue(VW)
        repeat(4) { m.addValue(PORSCHE) }

        m.printHistogram()

        captureOutput { m.printHistogram() }.stdout shouldBe """
                Summary of: 'CategoryMonitor.1'
                # Records: 7
                # Levels: 3
                
                Histogram of: 'CategoryMonitor.1'
                              bin |  values |  pct |                                         
                AUDI              |    2.00 |  .29 | ***********                             
                VW                |    1.00 |  .14 | ******                                  
                PORSCHE           |    4.00 |  .57 | ***********************
                                """.trimIndent()

        captureOutput { m.printHistogram(values = listOf(AUDI, TOYOTA)) }.stdout shouldBe """
                Summary of: 'CategoryMonitor.1'
                # Records: 7
                # Levels: 3
                
                Histogram of: 'CategoryMonitor.1'
                              bin |  values |  pct |                                         
                AUDI              |    2.00 |  .29 | ***********                             
                TOYOTA            |     .00 |  .00 |                                         
                rest              |    5.00 |  .71 | *****************************
                """.trimIndent()

        captureOutput { m.printHistogram(sortByWeight = true) }.stdout shouldBe """
                Summary of: 'CategoryMonitor.1'
                # Records: 7
                # Levels: 3
                
                Histogram of: 'CategoryMonitor.1'
                              bin |  values |  pct |                                         
                PORSCHE           |    4.00 |  .57 | ***********************                 
                AUDI              |    2.00 |  .29 | ***********                             
                VW                |    1.00 |  .14 | ******
                """.trimIndent()
    }


    @Test
    fun `Frequency level stats should be correct`() = createTestSimulation {
        val m = CategoryTimeline(AUDI)
//        m.addValue(AUDI)
        run(until = 2.tt)

        m.addValue(VW)
        run(until = 8.tt)

        m.getPct(AUDI) shouldBe 0.25

        // todo add test assertions
    }


    @Test
    fun `it should correctly calculate numeric level stats`() = createTestSimulation {
        val nlm = IntTimeline()

        run(2)
        nlm.addValue(2)

        run(2)
        nlm.addValue(6)
        run(4)

//            expected value (2*0 + 2*2 + 4*6)/8
        nlm.statistics().mean shouldBe 3.5.plusOrMinus(.1)

        nlm.printHistogram(valueBins = false)
        nlm.printHistogram(valueBins = true)

        //TODO add test assertions here
    }

    @Test
    fun `MetricTimeline should allow to retrieve a value for now but not before recording start`() =
        createTestSimulation {
            val nlm = IntTimeline()

            run(2)
            nlm.addValue(2)

            run(2)
            nlm.addValue(6)
            run(4)


            // first we try a get that should fail because it precedes simulation start
            shouldThrow<java.lang.IllegalArgumentException> {
                nlm[-1.0]
            }

            // now we try to get a value for `now`
            nlm[now] shouldBe 6
        }


    @Test
    fun `CategoryTimeline should allow to retrieve a value for now but not before recording start`() =
        createTestSimulation {
            val nlm = CategoryTimeline("foo")

            run(2)
            nlm.addValue("bar")

            run(2)
            nlm.addValue("kalasim")
            run(4)


            // first we try a get that should fail because it precedes simulation start
            shouldThrow<java.lang.IllegalArgumentException> {
                nlm[-1.0]
            }

            // now we try to get a value for `now`
            nlm[now] shouldBe "kalasim"
        }

    @Test
    fun `it should correctly calculate numeric  stats`() = createTestSimulation {
        val nsm = NumericStatisticMonitor()

        run(2)
        nsm.addValue(2)

        run(2)
        nsm.addValue(6)
        run(4)

        nsm.statistics().mean shouldBe 4.0
    }

    @Test
    fun `disabled monitor should error nicely when being queried`() = createTestSimulation {
        //FrequencyMonitor
        run {
            val nsm = CategoryMonitor<Int>()

            nsm.addValue(2)
            nsm.enabled = false

            shouldThrow<IllegalArgumentException> {
                nsm.statistics.size
            }

            // should be silently ignores
            nsm.addValue(2)

            shouldThrow<IllegalArgumentException> {
                nsm.printHistogram()
            }
        }
    }

    // TODO test the others
}


/* Test the monitors can be merged. */
class MergeTimelineTests {

    @Test
    fun `it should metric timelines`() = createTestSimulation {
        val mtA = IntTimeline()
        val mtB = IntTimeline()

        run(until = 5.tt)
        mtA.addValue(23)

        run(until = 10.tt)
        mtB.addValue(3)

        run(until = 12.tt)
        mtB.addValue(5)

        run(until = 14.tt)
        mtA.addValue(10)

        //merge statistics

        val addedTL = mtA + mtB

        println(addedTL.timestamps)
        println(addedTL.values)

        addedTL.values shouldBe listOf(0.0, 23.0, 26.0, 28.0, 15.0)
        addedTL.timestamps shouldBe listOf(0.0, 5.0, 10.0, 12.0, 14.0).map { it.tt }

        // just make sure that the other ops do not error do not error
        println(mtA + mtB)
        println(mtA - mtB)
        println(mtA * mtB)
        println(mtA / mtB)
    }
}


/* Test if monitors stats can be merged. */
class MergeMonitorStatsTests {

    @Test
    fun `it should merge NLM stats`() = createTestSimulation {
        val nlmA = IntTimeline()
        val nlmB = IntTimeline()

        run(until = 5.tt)
        nlmA.addValue(23)

        run(until = 10.tt)
        nlmB.addValue(3)

        run(until = 12.tt)
        nlmB.addValue(5)

        run(until = 14.tt)
        nlmA.addValue(10)

        //merge statistics
        val mergedStats: EnumeratedDistribution<Int> = listOf(nlmA, nlmB).mergeStats()
        println(mergedStats)

        // TODO tadd actual assert
    }


    @Test
    fun `it should merge FLM stats`() = createTestSimulation {
        val flmA = CategoryTimeline(1)
        val flmB = CategoryTimeline(2)

        flmA.addValue(1)
        flmB.addValue(2)

        run(until=1.tt)
        flmB.addValue(4)

        run(until=3.tt)
        flmA.addValue(1)

        //merge statistics
        run(until=5.tt)

        val mergedStats: EnumeratedDistribution<Int> = listOf(flmA, flmB).mergeStats()
        println(mergedStats)

        // TODO tadd actual assert
    }


    @Test
    fun `it should merge NSM stats`() = createTestSimulation {
        // note NSM.statistics() // delegates to StatisticalSummary (so should be mergeable as well)
        val nsmA = NumericStatisticMonitor()
        val nsmB = NumericStatisticMonitor() // delegates to StatisticalSummary (so should be mergeable as well)

        nsmA.apply {
            addValue(1)
            addValue(1)
        }

        nsmB.apply {
            addValue(3)
            addValue(3)
        }

        val mergedStats: StatisticalSummaryValues = listOf(nsmA.statistics(), nsmB.statistics()).merge()
        println(mergedStats)
        mergedStats.mean shouldBe 2
    }


    @Test
    fun `it should merge FSM stats`() = createTestSimulation {
        val fsmA = CategoryMonitor<Int>()
        val fsmB = CategoryMonitor<Int>()

        fsmA.apply {
            addValue(1)
            addValue(1)
        }

        fsmB.apply {
            addValue(3)
            addValue(3)
        }

        print(fsmA.snapshot)

        val mergedDist: FrequencyTable<Int> = listOf(fsmA, fsmB).mergeStats()

        //todo test this
        print(mergedDist)

        // TODO tadd actual assert
    }
}