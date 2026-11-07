package com.dopplertask.dopplertask.service;

/** Run ids count per customer, so a run is only known by both together. */
record RunOfCustomer(String customer, long executionId) {
}
