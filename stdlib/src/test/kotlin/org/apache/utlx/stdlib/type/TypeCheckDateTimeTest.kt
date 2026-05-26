package org.apache.utlx.stdlib.type

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime as JavaLocalDateTime

/**
 * F19: Tests for isDate, isDateTime, isLocalDateTime, isTime type checks.
 */
class TypeCheckDateTimeTest {

    @Test fun `isDate - true for UDM Date`() {
        assertEquals(true, (TypeFunctions.isDate(listOf(UDM.Date(LocalDate.of(2026, 1, 15)))) as UDM.Scalar).value)
    }
    @Test fun `isDate - false for string`() {
        assertEquals(false, (TypeFunctions.isDate(listOf(UDM.Scalar("2026-01-15"))) as UDM.Scalar).value)
    }
    @Test fun `isDate - false for number`() {
        assertEquals(false, (TypeFunctions.isDate(listOf(UDM.Scalar(42))) as UDM.Scalar).value)
    }
    @Test fun `isDate - false for null`() {
        assertEquals(false, (TypeFunctions.isDate(listOf(UDM.Scalar.nullValue())) as UDM.Scalar).value)
    }
    @Test fun `isDate - false for DateTime`() {
        assertEquals(false, (TypeFunctions.isDate(listOf(UDM.DateTime(Instant.now()))) as UDM.Scalar).value)
    }

    @Test fun `isDateTime - true for UDM DateTime`() {
        assertEquals(true, (TypeFunctions.isDateTime(listOf(UDM.DateTime(Instant.now()))) as UDM.Scalar).value)
    }
    @Test fun `isDateTime - false for Date`() {
        assertEquals(false, (TypeFunctions.isDateTime(listOf(UDM.Date(LocalDate.now()))) as UDM.Scalar).value)
    }
    @Test fun `isDateTime - false for string`() {
        assertEquals(false, (TypeFunctions.isDateTime(listOf(UDM.Scalar("2026-01-15T10:30:00Z"))) as UDM.Scalar).value)
    }

    @Test fun `isLocalDateTime - true for UDM LocalDateTime`() {
        assertEquals(true, (TypeFunctions.isLocalDateTime(listOf(UDM.LocalDateTime(JavaLocalDateTime.of(2026, 1, 15, 10, 30)))) as UDM.Scalar).value)
    }
    @Test fun `isLocalDateTime - false for DateTime`() {
        assertEquals(false, (TypeFunctions.isLocalDateTime(listOf(UDM.DateTime(Instant.now()))) as UDM.Scalar).value)
    }
    @Test fun `isLocalDateTime - false for string`() {
        assertEquals(false, (TypeFunctions.isLocalDateTime(listOf(UDM.Scalar("2026-01-15T10:30:00"))) as UDM.Scalar).value)
    }

    @Test fun `isTime - true for UDM Time`() {
        assertEquals(true, (TypeFunctions.isTime(listOf(UDM.Time(LocalTime.of(14, 30, 0)))) as UDM.Scalar).value)
    }
    @Test fun `isTime - false for DateTime`() {
        assertEquals(false, (TypeFunctions.isTime(listOf(UDM.DateTime(Instant.now()))) as UDM.Scalar).value)
    }
    @Test fun `isTime - false for string`() {
        assertEquals(false, (TypeFunctions.isTime(listOf(UDM.Scalar("14:30:00"))) as UDM.Scalar).value)
    }
    @Test fun `isTime - false for number`() {
        assertEquals(false, (TypeFunctions.isTime(listOf(UDM.Scalar(1430))) as UDM.Scalar).value)
    }
}
