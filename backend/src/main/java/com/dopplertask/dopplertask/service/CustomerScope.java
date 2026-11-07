package com.dopplertask.dopplertask.service;

/**
 * Whose work a thread is doing. An install runs for one customer, so nothing here does anything until an add-on says
 * otherwise. Work that outlives a request, such as a schedule, has to carry the customer along.
 */
public interface CustomerScope {
    /** The scope of an install that serves one customer. */
    CustomerScope ONE_CUSTOMER = new CustomerScope() {
    };

    /** Runs the work once for every customer. */
    default void forEachCustomer(Runnable work) {
        work.run();
    }

    /** The name of the current customer, which keeps same-named work apart. Empty for the one local customer. */
    default String current() {
        return "";
    }

    /** Work that does the same for the current customer, on whichever thread it later runs. */
    default Runnable bound(Runnable work) {
        return work;
    }
}
