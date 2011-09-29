/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.kernel.participants

import org.junit.Test
import org.junit.Assert._
import net.lshift.diffa.kernel.config.{DateTimeTypeDescriptor, DateTypeDescriptor}
import net.lshift.diffa.participant.scanning.{InvalidAttributeValueException, DateRangeConstraint, TimeRangeConstraint}
import org.joda.time.{DateTimeZone, LocalDate, DateTime}

class DatePartitionTest {

  @Test
  def dailyPartition = {
    val date = new DateTime(2012, 5, 5 ,8, 4, 2, 0, DateTimeZone.UTC)
    val function = DailyCategoryFunction("bizDate", DateDataType)
    val partition = function.bucket(date.toString)
    assertEquals("2012-05-05", partition)
  }

  @Test
  def monthlyPartition = {
    val date = new DateTime(2017, 9, 17 , 9, 9, 6, 0, DateTimeZone.UTC)
    val function = MonthlyCategoryFunction("bizDate", DateDataType)
    val partition = function.bucket(date.toString)
    assertEquals("2017-09", partition)
  }
  
  @Test
  def yearlyPartition = {
    val date = new DateTime(1998, 11, 21 , 15, 49, 55, 0, DateTimeZone.UTC)
    val function = YearlyCategoryFunction("bizDate", DateDataType)
    val partition = function.bucket(date.toString)
    assertEquals("1998", partition)
  }

  @Test
  def shouldParseFullIsoDate = {
    val date = "1998-11-21T15:49:55.000Z"
    val function = DailyCategoryFunction("bizDate", DateDataType)
    val partition = function.bucket(date)
    assertEquals("1998-11-21", partition)
  }

  @Test
  def shouldParseYYYYMMddDate = {
    val date = "1998-11-21"
    val function = DailyCategoryFunction("bizDate", DateDataType)
    val partition = function.bucket(date)
    assertEquals("1998-11-21", partition)
  }

  @Test
  def shouldNotWidenConstrainedRangeForYearlyPartition = {
    val previous = new DateRangeConstraint("bizDate", new LocalDate(1979,7,3), new LocalDate(1979,8,15))
    assertEquals(previous, YearlyCategoryFunction("bizDate", DateDataType).constrain(Some(previous), "1979"))
  }

  @Test
  def shouldNotWidenConstrainedRangeForYearlyPartitionWhenInitialConstraintIsLarge = {
    val previous = new DateRangeConstraint("bizDate", new LocalDate(1979,7,3), new LocalDate(1981,8,15))
    assertEquals(new DateRangeConstraint("bizDate", new LocalDate(1979,7,3), new LocalDate(1979,12,31)),
      YearlyCategoryFunction("bizDate", DateDataType).constrain(Some(previous), "1979"))
    assertEquals(new DateRangeConstraint("bizDate", new LocalDate(1980,1,1), new LocalDate(1980,12,31)),
      YearlyCategoryFunction("bizDate", DateDataType).constrain(Some(previous), "1980"))
  }

  @Test
  def shouldNotWidenConstrainedRangeForMonthlyPartition = {
    val previous = new DateRangeConstraint("bizDate", new LocalDate(1991,2,2), new LocalDate(1991,2,27))
    assertEquals(previous, MonthlyCategoryFunction("bizDate", DateDataType).constrain(Some(previous), "1991-02"))
  }

  @Test
  def shouldNotWidenConstrainedRangeForMonthlyPartitionWhenInitialConstraintIsLarge = {
    val previous = new DateRangeConstraint("bizDate", new LocalDate(1991,2,2), new LocalDate(1991,5,27))
    assertEquals(new DateRangeConstraint("bizDate", new LocalDate(1991,2,2), new LocalDate(1991,2,28)),
      MonthlyCategoryFunction("bizDate", DateDataType).constrain(Some(previous), "1991-02"))
    assertEquals(new DateRangeConstraint("bizDate", new LocalDate(1991,3,1), new LocalDate(1991,3,31)),
      MonthlyCategoryFunction("bizDate", DateDataType).constrain(Some(previous), "1991-03"))
  }

  @Test(expected=classOf[InvalidAttributeValueException])
  def shouldThrowInvalidCategoryExceptionIfValueIsNotDate {
    DailyCategoryFunction("bizDate", DateDataType).bucket("NOT_A_DATE")
  }

  @Test
  def descendFromYearlyCategoryFunction {
    val expectedStart = new DateTime(1986, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC)
    val expectedEnd = new DateTime(1987, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).minusMillis(1)
    assertEquals(Some(MonthlyCategoryFunction("bizDate", TimeDataType)), YearlyCategoryFunction("bizDate", TimeDataType).descend)
    assertEquals(new TimeRangeConstraint("bizDate", expectedStart, expectedEnd),
      YearlyCategoryFunction("bizDate", TimeDataType).constrain(None, "1986"))

    val expectedStartAsLocal = expectedStart.toLocalDate
    val expectedEndAsLocal = expectedEnd.toLocalDate
    assertEquals(new DateRangeConstraint("bizDate", expectedStartAsLocal, expectedEndAsLocal),
      YearlyCategoryFunction("bizDate", DateDataType).constrain(None, "1986"))
  }

}