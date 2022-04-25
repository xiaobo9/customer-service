package com.chatopera.cc.util;

public class IP implements java.io.Serializable {

    private static final long serialVersionUID = -421278423658892060L;
    private String country;
    private String province;
    private String city;
    private String isp;
    private String region;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getIsp() {
        return isp;
    }

    public void setIsp(String isp) {
        this.isp = isp;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String toString() {
        if ("0".equals(this.province) || "0".equals(this.city)) {
            return this.country;
        }

        if (this.province != null) {
            return this.province;
        }
        if (this.city != null) {
            return this.city;
        }

        return this.getRegion() != null ? this.getRegion() : "未知";
    }
}
