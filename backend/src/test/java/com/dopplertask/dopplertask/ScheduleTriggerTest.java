package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.trigger.ScheduleTrigger;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

public class ScheduleTriggerTest {

    private static final ZoneId STOCKHOLM = ZoneId.of("Europe/Stockholm");
    // A Wednesday.
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 4, 10, 7, 30, 0, STOCKHOLM);

    @Test
    public void testEveryFifteenMinutesFollowsTheClockSuccess() {
        Assert.assertEquals(at(2026, 11, 4, 10, 15), next(schedule("MINUTES", "15")));
    }

    @Test
    public void testEveryFewSecondsSuccess() {
        Assert.assertEquals(ZonedDateTime.of(2026, 11, 4, 10, 7, 40, 0, STOCKHOLM), next(schedule("SECONDS", "10")));
    }

    @Test
    public void testEveryTwoHoursAtMinuteSuccess() {
        ScheduleTrigger trigger = schedule("HOURS", "2");
        trigger.setAtMinute("30");
        Assert.assertEquals(at(2026, 11, 4, 10, 30), next(trigger));
    }

    @Test
    public void testEveryDayAtNineRunsTomorrowSuccess() {
        ScheduleTrigger trigger = schedule("DAYS", "1");
        trigger.setAtHour("9");
        Assert.assertEquals(at(2026, 11, 5, 9, 0), next(trigger));
    }

    @Test
    public void testWeekdaysOnlySkipsTheWeekendSuccess() {
        ScheduleTrigger trigger = schedule("DAYS", "1");
        trigger.setAtHour("9");
        trigger.setWeekdaysOnly(true);
        // Friday 6 November, then Monday 9 November.
        Assert.assertEquals(at(2026, 11, 9, 9, 0), ScheduleTrigger.Schedule.Companion.of(trigger).nextAfter(at(2026, 11, 6, 9, 0)));
    }

    @Test
    public void testWeeklyOnChosenDaysSuccess() {
        ScheduleTrigger trigger = schedule("WEEKS", "1");
        trigger.setOnDays("mon, friday");
        trigger.setAtHour("8");
        Assert.assertEquals(at(2026, 11, 6, 8, 0), next(trigger));
    }

    @Test
    public void testEveryTwoWeeksSkipsTheWeekBetweenSuccess() {
        ScheduleTrigger trigger = schedule("WEEKS", "2");
        trigger.setOnDays("MON");
        trigger.setAtHour("8");
        ZonedDateTime first = next(trigger);
        ZonedDateTime second = ScheduleTrigger.Schedule.Companion.of(trigger).nextAfter(first);
        Assert.assertEquals(first.plusWeeks(2), second);
    }

    @Test
    public void testMonthlyOnTheThirtyFirstRunsOnTheLastDaySuccess() {
        ScheduleTrigger trigger = schedule("MONTHS", "1");
        trigger.setOnDayOfMonth("31");
        trigger.setAtHour("23");
        Assert.assertEquals(at(2026, 11, 30, 23, 0), next(trigger));
    }

    @Test
    public void testIntervalsThatDoNotFitTheUnitKeepTheirGapAcrossBoundariesSuccess() {
        Assert.assertEquals(Duration.ofMinutes(90), gap(schedule("MINUTES", "90"), 40));
        Assert.assertEquals(Duration.ofSeconds(90), gap(schedule("SECONDS", "90"), 80));
        Assert.assertEquals(Duration.ofHours(5), gap(schedule("HOURS", "5"), 20));
        Assert.assertEquals(Duration.ofHours(30), gap(schedule("HOURS", "30"), 10));
        // From October to the end of the year, with the clock change on 25 October in between.
        Assert.assertEquals(Duration.ofDays(2), gap(schedule("DAYS", "2"), 40));
    }

    @Test
    public void testEveryFiveMonthsKeepsFiveMonthsApartAcrossTheYearSuccess() {
        ScheduleTrigger trigger = schedule("MONTHS", "5");
        ZonedDateTime moment = next(trigger);
        for (int run = 0; run < 6; run++) {
            ZonedDateTime following = ScheduleTrigger.Schedule.Companion.of(trigger).nextAfter(moment);
            Assert.assertEquals(moment.plusMonths(5), following);
            moment = following;
        }
    }

    @Test
    public void testDayTheMonthDoesNotHaveMeansItsLastDaySuccess() {
        ScheduleTrigger trigger = schedule("MONTHS", "1");
        trigger.setOnDayOfMonth("30");
        Assert.assertEquals(at(2027, 2, 28, 0, 0), ScheduleTrigger.Schedule.Companion.of(trigger).nextAfter(at(2027, 1, 30, 0, 0)));
        Assert.assertEquals(at(2027, 3, 30, 0, 0), ScheduleTrigger.Schedule.Companion.of(trigger).nextAfter(at(2027, 2, 28, 0, 0)));
    }

    @Test
    public void testUnknownTimeZoneExplainsWhyFail() {
        ScheduleTrigger trigger = schedule("MINUTES", "5");
        trigger.setTimezone("Mars/Olympus");
        Assert.assertTrue(trigger.problem(), trigger.problem().contains("\"Mars/Olympus\" is not a time zone"));
        Assert.assertNull(schedule("MINUTES", "5").problem());
    }

    @Test
    public void testEveryAboveTheLimitExplainsWhyFail() {
        Assert.assertTrue(schedule("MINUTES", "10001").problem().contains("Every should be from 1 to 10000"));
    }

    @Test
    public void testCronWithFiveFieldsAndSeveralLinesSuccess() {
        ScheduleTrigger trigger = schedule("CRON", "1");
        trigger.setCronExpression("0 9 * * MON-FRI\n# a note\n30 10 * * *");
        Assert.assertEquals(at(2026, 11, 4, 10, 30), next(trigger));
    }

    @Test
    public void testCronWithSecondsAndMacroSuccess() {
        ScheduleTrigger trigger = schedule("CRON", "1");
        trigger.setCronExpression("@daily\n45 7 10 * * *");
        Assert.assertEquals(ZonedDateTime.of(2026, 11, 4, 10, 7, 45, 0, STOCKHOLM), next(trigger));
    }

    @Test
    public void testInvalidCronExplainsWhyFail() {
        ScheduleTrigger trigger = schedule("CRON", "1");
        trigger.setCronExpression("every morning");
        try {
            next(trigger);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("\"every morning\" is not valid"));
        }
    }

    @Test
    public void testUnknownWeekdayExplainsWhyFail() {
        ScheduleTrigger trigger = schedule("WEEKS", "1");
        trigger.setOnDays("MON,FUNDAY");
        try {
            next(trigger);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("\"FUNDAY\" is not a weekday"));
        }
    }

    @Test
    public void testMomentGivesTheSameValuesAsTheOtherToolsSuccess() {
        Map<String, String> moment = ScheduleTrigger.Companion.momentOf(at(2026, 11, 1, 9, 5));
        Assert.assertEquals("2026-11-01T09:05:00+01:00", moment.get("timestamp"));
        Assert.assertEquals("November 1st 2026, 9:05:00 am", moment.get("readableDate"));
        Assert.assertEquals("Sunday", moment.get("dayOfWeek"));
        Assert.assertEquals("01", moment.get("dayOfMonth"));
        Assert.assertEquals("Europe/Stockholm (UTC+01:00)", moment.get("timezone"));
    }

    private static ScheduleTrigger schedule(String rule, String every) {
        ScheduleTrigger trigger = new ScheduleTrigger();
        trigger.setRule(rule);
        trigger.setEvery(every);
        trigger.setTimezone("Europe/Stockholm");
        return trigger;
    }

    private static ZonedDateTime next(ScheduleTrigger trigger) {
        return ScheduleTrigger.Schedule.Companion.of(trigger).nextAfter(NOW);
    }

    /** The one gap between all the starts from 1 October, or a failure when they differ. */
    private static Duration gap(ScheduleTrigger trigger, int starts) {
        ScheduleTrigger.Schedule schedule = ScheduleTrigger.Schedule.Companion.of(trigger);
        ZonedDateTime moment = schedule.nextAfter(at(2026, 10, 1, 0, 0));
        Duration gap = null;
        for (int start = 0; start < starts; start++) {
            ZonedDateTime following = schedule.nextAfter(moment);
            Duration between = Duration.between(moment.toLocalDateTime(), following.toLocalDateTime());
            Assert.assertTrue("From " + moment + " to " + following, gap == null || gap.equals(between));
            gap = between;
            moment = following;
        }
        return gap;
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, STOCKHOLM);
    }
}
