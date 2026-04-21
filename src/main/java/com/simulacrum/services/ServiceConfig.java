package com.simulacrum.services;

/**
 * Host/port hooks for the external services a real SWFTS deployment would connect to.
 * MVP does not ship any of these servers — the UI just exposes the settings so the
 * integration point is visible.
 */
public final class ServiceConfig {
    public record Endpoint(String host, int port, boolean tls) {
    }

    private Endpoint ldap = new Endpoint("ldap.local", 389, false);
    private Endpoint secureLdap = new Endpoint("ldaps.local", 636, true);
    private Endpoint dns = new Endpoint("dns.local", 53, false);
    private Endpoint ntp = new Endpoint("ntp.local", 123, false);
    private Endpoint taclan = new Endpoint("taclan.local", 9000, true);

    public Endpoint ldap() { return ldap; }
    public Endpoint secureLdap() { return secureLdap; }
    public Endpoint dns() { return dns; }
    public Endpoint ntp() { return ntp; }
    public Endpoint taclan() { return taclan; }

    public void setLdap(Endpoint e) { this.ldap = e; }
    public void setSecureLdap(Endpoint e) { this.secureLdap = e; }
    public void setDns(Endpoint e) { this.dns = e; }
    public void setNtp(Endpoint e) { this.ntp = e; }
    public void setTaclan(Endpoint e) { this.taclan = e; }
}
