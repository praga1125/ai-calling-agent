package com.flowzo.callingagent.repository;

/** One row of a {@code group by} count, so totals are summed by the database, not in Java. */
public interface EnumCount<E extends Enum<E>> {

    E getValue();

    long getTotal();
}
