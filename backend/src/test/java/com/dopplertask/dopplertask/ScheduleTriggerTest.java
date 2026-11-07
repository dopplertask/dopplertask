package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.action.trigger.ScheduleTrigger;
import org.junit.Assert;
import org.junit.Test;

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

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, STOCKHOLM);
    }
}
