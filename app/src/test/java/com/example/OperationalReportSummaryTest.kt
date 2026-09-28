package com.example

import com.example.data.*
import com.example.viewmodel.CapitalRecoveryOverview
import com.example.viewmodel.OperationalCycleSummary
import com.example.viewmodel.OperationalDailySummary
import com.example.viewmodel.OperationalReportSummary
import com.example.viewmodel.computeOperationalCyclesFifo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class OperationalReportSummaryTest {

    @Test
    fun testProfitableReportMetrics() {
        val dailySummaries = listOf(
            OperationalDailySummary(
                dateString = "2026-09-18",
                displayDate = "Sep 18, 2026",
                timestamp = 1773800000000L,
                storeExpense = 2000.0,
                employeeExpense = 500.0,
                totalExpense = 2500.0,
                sales = 6000.0,
                netProfit = 3500.0,
                isProfitable = true,
                orderCount = 10,
                storeExpenses = listOf(OperationalExpense(id = 1, title = "Feeds Delivery", amount = 2000.0)),
                employeeExpenses = listOf(CashOutEntry(id = 1, cashierName = "Juan", amount = 500.0))
            )
        )

        val report = OperationalReportSummary(
            rangeLabel = "Last 7 Days",
            totalSales = 6000.0,
            totalStoreExpenses = 2000.0,
            totalEmployeeExpenses = 500.0,
            totalExpenses = 2500.0,
            netProfit = 3500.0,
            isProfitable = true,
            profitMargin = 58.33,
            recoveryRate = 240.0,
            totalOrders = 10,
            dailySummaries = dailySummaries,
            filteredStoreExpenses = listOf(OperationalExpense(id = 1, title = "Feeds Delivery", amount = 2000.0))
        )

        assertTrue(report.isProfitable)
        assertEquals(0.0, report.remainingToBreakEven, 0.001)
        assertEquals(3500.0, report.netProfit, 0.001)
        assertEquals(2500.0, report.totalExpenses, 0.001)
        assertEquals("Profit", dailySummaries[0].status)
    }

    @Test
    fun testDeficitReportMetrics() {
        val dailySummaries = listOf(
            OperationalDailySummary(
                dateString = "2026-09-19",
                displayDate = "Sep 19, 2026",
                timestamp = 1773900000000L,
                storeExpense = 10000.0,
                employeeExpense = 800.0,
                totalExpense = 10800.0,
                sales = 4000.0,
                netProfit = -6800.0,
                isProfitable = false,
                orderCount = 5,
                storeExpenses = listOf(OperationalExpense(id = 2, title = "Bulk Shipment", amount = 10000.0)),
                employeeExpenses = listOf(CashOutEntry(id = 2, cashierName = "Maria", amount = 800.0))
            )
        )

        val report = OperationalReportSummary(
            rangeLabel = "Today",
            totalSales = 4000.0,
            totalStoreExpenses = 10000.0,
            totalEmployeeExpenses = 800.0,
            totalExpenses = 10800.0,
            netProfit = -6800.0,
            isProfitable = false,
            profitMargin = -170.0,
            recoveryRate = 37.04,
            totalOrders = 5,
            dailySummaries = dailySummaries,
            filteredStoreExpenses = listOf(OperationalExpense(id = 2, title = "Bulk Shipment", amount = 10000.0))
        )

        assertFalse(report.isProfitable)
        assertEquals(6800.0, report.remainingToBreakEven, 0.001)
        assertEquals(-6800.0, report.netProfit, 0.001)
        assertEquals("Deficit", dailySummaries[0].status)
    }

    @Test
    fun testMultipleStoreExpensesDoNotResetCycle() {
        // Simulate continuous accumulation:
        // Day 1: Feeds delivery ₱5,000, Employee ₱300, Sales ₱3,000
        // Day 2: Supplies ₱500, Employee ₱300, Sales ₱4,000
        // Total Sales = ₱7,000
        // Total Store Expenses = ₱5,000 + ₱500 = ₱5,500
        // Total Employee Expenses = ₱600
        // Total Expenses = ₱6,100
        // Net Profit = ₱7,000 - ₱6,100 = ₱900 (Profitable!)

        val op1 = OperationalExpense(id = 1, title = "Feeds Delivery", amount = 5000.0, timestamp = 1000L)
        val op2 = OperationalExpense(id = 2, title = "Supplies", amount = 500.0, timestamp = 2000L)
        val opList = listOf(op1, op2)

        val cash1 = CashOutEntry(id = 1, cashierName = "Juan", amount = 300.0, timestamp = 1000L)
        val cash2 = CashOutEntry(id = 2, cashierName = "Juan", amount = 300.0, timestamp = 2000L)
        val cashList = listOf(cash1, cash2)

        val tx1 = TransactionRecord(id = 1, status = "PAID", subtotal = 3000.0, tax = 0.0, discount = 0.0, totalAmount = 3000.0, timestamp = 1000L)
        val tx2 = TransactionRecord(id = 2, status = "PAID", subtotal = 4000.0, tax = 0.0, discount = 0.0, totalAmount = 4000.0, timestamp = 2000L)
        val txList = listOf(tx1, tx2)

        val totalSales = txList.sumOf { it.totalAmount }.roundToCentavos()
        val totalStoreExp = opList.sumOf { it.amount }.roundToCentavos()
        val totalEmpExp = cashList.sumOf { it.amount }.roundToCentavos()
        val totalExp = (totalStoreExp + totalEmpExp).roundToCentavos()
        val netProfit = (totalSales - totalExp).roundToCentavos()

        assertEquals(7000.0, totalSales, 0.001)
        assertEquals(5500.0, totalStoreExp, 0.001)
        assertEquals(600.0, totalEmpExp, 0.001)
        assertEquals(6100.0, totalExp, 0.001)
        assertEquals(900.0, netProfit, 0.001)

        val report = OperationalReportSummary(
            rangeLabel = "All Time",
            totalSales = totalSales,
            totalStoreExpenses = totalStoreExp,
            totalEmployeeExpenses = totalEmpExp,
            totalExpenses = totalExp,
            netProfit = netProfit,
            isProfitable = netProfit >= 0.0,
            profitMargin = ((netProfit / totalSales) * 100.0).roundToCentavos(),
            recoveryRate = ((totalSales / totalExp) * 100.0).roundToCentavos(),
            totalOrders = txList.size,
            filteredStoreExpenses = opList
        )

        assertTrue("Should be profitable without cycle reset", report.isProfitable)
        assertEquals(0.0, report.remainingToBreakEven, 0.001)
        assertEquals(114.75, report.recoveryRate, 0.01)
    }

    @Test
    fun testBreakEvenStatus() {
        val dailySummary = OperationalDailySummary(
            dateString = "2026-09-17",
            displayDate = "Sep 17, 2026",
            timestamp = 1773700000000L,
            storeExpense = 1500.0,
            employeeExpense = 500.0,
            totalExpense = 2000.0,
            sales = 2000.0,
            netProfit = 0.0,
            isProfitable = true,
            orderCount = 4,
            storeExpenses = emptyList(),
            employeeExpenses = emptyList()
        )

        assertEquals("Break-Even", dailySummary.status)
        assertTrue(dailySummary.isProfitable)
    }

    @Test
    fun testCapitalRecoveryOverviewComputation() {
        val expense1 = OperationalExpense(id = 1, title = "Feeds Batch 1", amount = 10000.0, timestamp = 1000L)
        val expense2 = OperationalExpense(id = 2, title = "Feeds Batch 2", amount = 8000.0, timestamp = 2000L)
        val expense3 = OperationalExpense(id = 3, title = "Feeds Batch 3", amount = 5000.0, timestamp = 3000L)

        // Cycle 1 (closed): Sales 5000, Wages 1000, Total Cost 11000 -> Net: -6000 (Deficit)
        val cycle1 = OperationalCycleSummary(
            expense = expense1,
            periodStartTimestamp = 1000L,
            periodEndTimestamp = 2000L,
            periodSalesTotal = 5000.0,
            periodOrderCount = 5,
            periodEmployeeExpenses = 1000.0,
            totalPeriodCost = 11000.0,
            netBalance = -6000.0,
            recoveryRate = 45.45,
            isActive = false
        )

        // Cycle 2 (closed): Sales 10000, Wages 500, Total Cost 8500 -> Net: +1500 (Recovered)
        val cycle2 = OperationalCycleSummary(
            expense = expense2,
            periodStartTimestamp = 2000L,
            periodEndTimestamp = 3000L,
            periodSalesTotal = 10000.0,
            periodOrderCount = 8,
            periodEmployeeExpenses = 500.0,
            totalPeriodCost = 8500.0,
            netBalance = 1500.0,
            recoveryRate = 117.65,
            isActive = false
        )

        // Cycle 3 (active): Sales 7000, Wages 500, Total Cost 5500 -> Net: +1500 (Recovered)
        val cycle3 = OperationalCycleSummary(
            expense = expense3,
            periodStartTimestamp = 3000L,
            periodEndTimestamp = null,
            periodSalesTotal = 7000.0,
            periodOrderCount = 6,
            periodEmployeeExpenses = 500.0,
            totalPeriodCost = 5500.0,
            netBalance = 1500.0,
            recoveryRate = 127.27,
            isActive = true
        )

        val cycles = listOf(cycle3, cycle2, cycle1)
        val netProfit = cycles.sumOf { it.netBalance }.roundToCentavos()
        val activeCycle = cycles.firstOrNull { it.isActive }
        val closedCycles = cycles.filterNot { it.isActive }
        val closedDeficits = closedCycles.filter { it.netBalance < 0.0 }

        val overview = CapitalRecoveryOverview(
            totalNetProfit = netProfit,
            isOverallProfitable = netProfit >= 0.0,
            activeCycleOutstanding = activeCycle?.let { if (it.netBalance < 0.0) -it.netBalance else 0.0 }?.roundToCentavos() ?: 0.0,
            activeCycleRecoveryRate = activeCycle?.recoveryRate ?: 100.0,
            historicalUnrecoveredCount = closedDeficits.size,
            historicalUnrecoveredTotal = closedDeficits.sumOf { -it.netBalance }.roundToCentavos(),
            totalCycles = cycles.size
        )

        assertEquals(-3000.0, overview.totalNetProfit, 0.001)
        assertFalse(overview.isOverallProfitable)
        assertEquals(0.0, overview.activeCycleOutstanding, 0.001)
        assertEquals(127.27, overview.activeCycleRecoveryRate, 0.01)
        assertEquals(1, overview.historicalUnrecoveredCount)
        assertEquals(6000.0, overview.historicalUnrecoveredTotal, 0.001)
        assertEquals(3, overview.totalCycles)
    }

    @Test
    fun testCapitalRecoveryOverviewWithActiveDeficit() {
        val expense1 = OperationalExpense(id = 1, title = "Feeds Batch 1", amount = 5000.0, timestamp = 1000L)
        val expense2 = OperationalExpense(id = 2, title = "Feeds Batch 2", amount = 5000.0, timestamp = 2000L)

        val cycle1 = OperationalCycleSummary(
            expense = expense1,
            periodStartTimestamp = 1000L,
            periodEndTimestamp = 2000L,
            periodSalesTotal = 3000.0,
            periodOrderCount = 3,
            periodEmployeeExpenses = 0.0,
            totalPeriodCost = 5000.0,
            netBalance = -2000.0,
            recoveryRate = 60.0,
            isActive = false
        )

        val cycle2 = OperationalCycleSummary(
            expense = expense2,
            periodStartTimestamp = 2000L,
            periodEndTimestamp = null,
            periodSalesTotal = 3500.0,
            periodOrderCount = 4,
            periodEmployeeExpenses = 500.0,
            totalPeriodCost = 5500.0,
            netBalance = -2000.0,
            recoveryRate = 63.64,
            isActive = true
        )

        val cycles = listOf(cycle2, cycle1)
        val netProfit = cycles.sumOf { it.netBalance }.roundToCentavos()
        val activeCycle = cycles.firstOrNull { it.isActive }
        val closedCycles = cycles.filterNot { it.isActive }
        val closedDeficits = closedCycles.filter { it.netBalance < 0.0 }

        val overview = CapitalRecoveryOverview(
            totalNetProfit = netProfit,
            isOverallProfitable = netProfit >= 0.0,
            activeCycleOutstanding = activeCycle?.let { if (it.netBalance < 0.0) -it.netBalance else 0.0 }?.roundToCentavos() ?: 0.0,
            activeCycleRecoveryRate = activeCycle?.recoveryRate ?: 100.0,
            historicalUnrecoveredCount = closedDeficits.size,
            historicalUnrecoveredTotal = closedDeficits.sumOf { -it.netBalance }.roundToCentavos(),
            totalCycles = cycles.size
        )

        assertEquals(-4000.0, overview.totalNetProfit, 0.001)
        assertFalse(overview.isOverallProfitable)
        assertEquals(2000.0, overview.activeCycleOutstanding, 0.001)
        assertEquals(63.64, overview.activeCycleRecoveryRate, 0.01)
        assertEquals(1, overview.historicalUnrecoveredCount)
        assertEquals(2000.0, overview.historicalUnrecoveredTotal, 0.001)
    }

    @Test
    fun testCapitalRecoveryOverviewHistoricalLossWithProfitableHeadline() {
        // Closed cycle had a ₱2,000 loss, but current active cycle has ₱5,000 profit.
        // Overall is profitable (+₱3,000) despite 1 past deficit!
        val expense1 = OperationalExpense(id = 1, title = "Old Batch", amount = 5000.0, timestamp = 1000L)
        val expense2 = OperationalExpense(id = 2, title = "Current Batch", amount = 5000.0, timestamp = 2000L)

        val cycle1 = OperationalCycleSummary(
            expense = expense1,
            periodStartTimestamp = 1000L,
            periodEndTimestamp = 2000L,
            periodSalesTotal = 3000.0,
            periodOrderCount = 3,
            periodEmployeeExpenses = 0.0,
            totalPeriodCost = 5000.0,
            netBalance = -2000.0,
            recoveryRate = 60.0,
            isActive = false
        )

        val cycle2 = OperationalCycleSummary(
            expense = expense2,
            periodStartTimestamp = 2000L,
            periodEndTimestamp = null,
            periodSalesTotal = 10000.0,
            periodOrderCount = 10,
            periodEmployeeExpenses = 0.0,
            totalPeriodCost = 5000.0,
            netBalance = 5000.0,
            recoveryRate = 200.0,
            isActive = true
        )

        val cycles = listOf(cycle2, cycle1)
        val netProfit = cycles.sumOf { it.netBalance }.roundToCentavos()
        val activeCycle = cycles.firstOrNull { it.isActive }
        val closedCycles = cycles.filterNot { it.isActive }
        val closedDeficits = closedCycles.filter { it.netBalance < 0.0 }

        val overview = CapitalRecoveryOverview(
            totalNetProfit = netProfit,
            isOverallProfitable = netProfit >= 0.0,
            activeCycleOutstanding = activeCycle?.let { if (it.netBalance < 0.0) -it.netBalance else 0.0 }?.roundToCentavos() ?: 0.0,
            activeCycleRecoveryRate = activeCycle?.recoveryRate ?: 100.0,
            historicalUnrecoveredCount = closedDeficits.size,
            historicalUnrecoveredTotal = closedDeficits.sumOf { -it.netBalance }.roundToCentavos(),
            totalCycles = cycles.size
        )

        assertTrue("Should be overall profitable", overview.isOverallProfitable)
        assertEquals(3000.0, overview.totalNetProfit, 0.001)
        assertEquals(0.0, overview.activeCycleOutstanding, 0.001)
        assertEquals(200.0, overview.activeCycleRecoveryRate, 0.01)
        assertEquals(1, overview.historicalUnrecoveredCount)
        assertEquals(2000.0, overview.historicalUnrecoveredTotal, 0.001)
    }

    @Test
    fun testCapitalRecoveryOverviewEmptyCycles() {
        val overview = CapitalRecoveryOverview(
            totalNetProfit = 0.0,
            isOverallProfitable = true,
            activeCycleOutstanding = 0.0,
            activeCycleRecoveryRate = 100.0,
            historicalUnrecoveredCount = 0,
            historicalUnrecoveredTotal = 0.0,
            totalCycles = 0
        )

        assertTrue(overview.isOverallProfitable)
        assertEquals(0.0, overview.totalNetProfit, 0.001)
        assertEquals(0, overview.totalCycles)
        assertEquals(0.0, overview.activeCycleOutstanding, 0.001)
    }

    @Test
    fun testCycleBucketingFiltersPaidOnlyAndUsesEffectiveSettledTimestamp() {
        val opList = listOf(
            OperationalExpense(id = 1, title = "Batch 1", amount = 5000.0, timestamp = 1000L),
            OperationalExpense(id = 2, title = "Batch 2", amount = 5000.0, timestamp = 2000L)
        )

        val txList = listOf(
            // Tx 1: Created at 1100, PAID -> should be in Cycle 1
            TransactionRecord(id = 1, totalAmount = 1500.0, subtotal = 1500.0, tax = 0.0, discount = 0.0, status = "PAID", timestamp = 1100L),
            // Tx 2: Created at 1200, UNPAID -> should NOT be counted in any cycle
            TransactionRecord(id = 2, totalAmount = 3000.0, subtotal = 3000.0, tax = 0.0, discount = 0.0, status = "UNPAID", timestamp = 1200L),
            // Tx 3: Created at 1300 as UNPAID, but settled at 2100 as PAID -> effective time is 2100, so belongs in Cycle 2!
            TransactionRecord(id = 3, totalAmount = 2500.0, subtotal = 2500.0, tax = 0.0, discount = 0.0, status = "PAID", timestamp = 1300L, settledTimestamp = 2100L)
        )

        // Cycle 1 window: [1000L, 2000L)
        val cycle1Txs = txList.filter { tx ->
            if (tx.status != "PAID") return@filter false
            val effectiveTime = tx.settledTimestamp ?: tx.timestamp
            effectiveTime in 1000L until 2000L
        }
        // Only tx1 should be in Cycle 1
        assertEquals(1, cycle1Txs.size)
        assertEquals(1, cycle1Txs[0].id)
        assertEquals(1500.0, cycle1Txs.sumOf { it.totalAmount }, 0.001)

        // Cycle 2 window: [2000L, null)
        val cycle2Txs = txList.filter { tx ->
            if (tx.status != "PAID") return@filter false
            val effectiveTime = tx.settledTimestamp ?: tx.timestamp
            effectiveTime >= 2000L
        }
        // tx3 settled at 2100L, so it should be in Cycle 2
        assertEquals(1, cycle2Txs.size)
        assertEquals(3, cycle2Txs[0].id)
        assertEquals(2500.0, cycle2Txs.sumOf { it.totalAmount }, 0.001)
    }

    @Test
    fun testFifoSequentialTrackingUnrecoveredFirstExpenseQueuesSecondExpense() {
        // Feeds logged at 1000L for ₱14,595
        // Plastic logged at 1000L for ₱4,000
        val exp1 = OperationalExpense(id = 1, title = "FEEDS", amount = 14595.0, timestamp = 1000L)
        val exp2 = OperationalExpense(id = 2, title = "PLASTIC", amount = 4000.0, timestamp = 1000L)
        val opList = listOf(exp1, exp2)

        // 38 paid sales totaling ₱2,761 at 1500L
        val txList = listOf(
            TransactionRecord(id = 1, totalAmount = 2761.0, subtotal = 2761.0, tax = 0.0, discount = 0.0, status = "PAID", timestamp = 1500L)
        )

        val cycles = computeOperationalCyclesFifo(opList, txList, emptyList())
        assertEquals(2, cycles.size)

        // cycles is reversed (newest first): [PLASTIC, FEEDS]
        val plasticCycle = cycles[0]
        val feedsCycle = cycles[1]

        // FEEDS was logged first, so it receives the ₱2,761 sales
        assertEquals("FEEDS", feedsCycle.expense.title)
        assertEquals(2761.0, feedsCycle.periodSalesTotal, 0.001)
        assertEquals(1, feedsCycle.periodOrderCount)
        assertEquals(14595.0, feedsCycle.totalPeriodCost, 0.001)
        assertEquals(-11834.0, feedsCycle.netBalance, 0.001)
        assertEquals(18.92, feedsCycle.recoveryRate, 0.01)
        assertTrue(feedsCycle.isActive)
        assertFalse(feedsCycle.isQueued)
        assertFalse(feedsCycle.isProfitable)

        // PLASTIC was logged second, and FEEDS is not yet covered, so PLASTIC is queued with ₱0 sales
        assertEquals("PLASTIC", plasticCycle.expense.title)
        assertEquals(0.0, plasticCycle.periodSalesTotal, 0.001)
        assertEquals(0, plasticCycle.periodOrderCount)
        assertEquals(4000.0, plasticCycle.totalPeriodCost, 0.001)
        assertEquals(-4000.0, plasticCycle.netBalance, 0.001)
        assertEquals(0.0, plasticCycle.recoveryRate, 0.001)
        assertFalse(plasticCycle.isActive)
        assertTrue(plasticCycle.isQueued)
    }

    @Test
    fun testFifoSequentialTrackingOverflowRecoversFirstAndBeginsSecond() {
        val exp1 = OperationalExpense(id = 1, title = "FEEDS", amount = 10000.0, timestamp = 1000L)
        val exp2 = OperationalExpense(id = 2, title = "PLASTIC", amount = 4000.0, timestamp = 1000L)
        val opList = listOf(exp1, exp2)

        // Tx1: ₱6,000 at 1200L
        // Tx2: ₱6,000 at 1400L -> total sales = ₱12,000
        val txList = listOf(
            TransactionRecord(id = 1, totalAmount = 6000.0, subtotal = 6000.0, tax = 0.0, discount = 0.0, status = "PAID", timestamp = 1200L),
            TransactionRecord(id = 2, totalAmount = 6000.0, subtotal = 6000.0, tax = 0.0, discount = 0.0, status = "PAID", timestamp = 1400L)
        )

        val cycles = computeOperationalCyclesFifo(opList, txList, emptyList())
        val plasticCycle = cycles[0]
        val feedsCycle = cycles[1]

        // FEEDS required ₱10,000. It took ₱6,000 from tx1, plus ₱4,000 from tx2 -> ₱10,000 total (100% recovered)
        assertEquals("FEEDS", feedsCycle.expense.title)
        assertEquals(10000.0, feedsCycle.periodSalesTotal, 0.001)
        assertEquals(2, feedsCycle.periodOrderCount)
        assertEquals(0.0, feedsCycle.netBalance, 0.001)
        assertEquals(100.0, feedsCycle.recoveryRate, 0.001)
        assertFalse(feedsCycle.isActive)
        assertFalse(feedsCycle.isQueued)
        assertTrue(feedsCycle.isProfitable)

        // PLASTIC took the remaining ₱2,000 overflow from tx2 -> 50% covered, currently ONGOING
        assertEquals("PLASTIC", plasticCycle.expense.title)
        assertEquals(2000.0, plasticCycle.periodSalesTotal, 0.001)
        assertEquals(1, plasticCycle.periodOrderCount)
        assertEquals(-2000.0, plasticCycle.netBalance, 0.001)
        assertEquals(50.0, plasticCycle.recoveryRate, 0.001)
        assertTrue(plasticCycle.isActive)
        assertFalse(plasticCycle.isQueued)
    }

    @Test
    fun testFifoWithEmployeeWagesIncreasesActiveCost() {
        val exp1 = OperationalExpense(id = 1, title = "FEEDS", amount = 5000.0, timestamp = 1000L)
        val cashOut = CashOutEntry(id = 1, cashierName = "Juan", amount = 500.0, timestamp = 1100L)
        val tx = TransactionRecord(id = 1, totalAmount = 5200.0, subtotal = 5200.0, tax = 0.0, discount = 0.0, status = "PAID", timestamp = 1200L)

        val cycles = computeOperationalCyclesFifo(listOf(exp1), listOf(tx), listOf(cashOut))
        val cycle = cycles[0]

        // Total cost should be ₱5,000 + ₱500 = ₱5,500
        assertEquals(5500.0, cycle.totalPeriodCost, 0.001)
        assertEquals(500.0, cycle.periodEmployeeExpenses, 0.001)
        assertEquals(5200.0, cycle.periodSalesTotal, 0.001)
        assertEquals(-300.0, cycle.netBalance, 0.001)
        assertEquals(94.55, cycle.recoveryRate, 0.01)
        assertTrue(cycle.isActive)
        assertFalse(cycle.isProfitable)
    }
}
