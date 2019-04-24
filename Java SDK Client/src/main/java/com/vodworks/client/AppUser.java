package com.vodworks.client;

import java.util.Set;
import java.io.Serializable;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.Enrollment;

public class AppUser implements User, Serializable {

    private static final long serializationId = 1L;

    private String name;
    private String mspId;
    private String account;
    private Set<String> roles;
    private String affiliation;
    private Enrollment enrollment;

    public AppUser() {
        // no-arg constructor
    }

    public AppUser(String name, String affiliation, String mspId, Enrollment enrollment) {
        this.name = name;
        this.mspId = mspId;
        this.enrollment = enrollment;
        this.affiliation = affiliation;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    @Override
    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    @Override
    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    @Override
    public Enrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    @Override
    public String getMspId() {
        return mspId;
    }

    public void setMspId(String mspId) {
        this.mspId = mspId;
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "name='" + name + '\'' +
                "\n, roles=" + roles +
                "\n, account='" + account + '\'' +
                "\n, affiliation='" + affiliation + '\'' +
                "\n, enrollment=" + enrollment +
                "\n, mspId='" + mspId + '\'' +
                '}';
    }
}