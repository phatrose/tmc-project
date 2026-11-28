package vn.tmc.central.reportingaudit;

import java.time.*;

public final class MutableClock extends Clock {
    private volatile Instant at;
    public MutableClock(Instant at){this.at=at;}
    public void set(Instant at){this.at=at;}
    public void advance(Duration duration){at=at.plus(duration);}
    @Override public ZoneId getZone(){return ZoneOffset.UTC;}
    @Override public Clock withZone(ZoneId zone){return Clock.fixed(at,zone);}
    @Override public Instant instant(){return at;}
}
