package com.smartlibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared LU Centralized System mail identity (must match Attendance Management).
 */
@ConfigurationProperties(prefix = "central.mail")
public class CentralMailProperties {

    private String fromName = "LU Centralized System";
    private String fromEmail = "noreply.lu.system@gmail.com";

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    /** RFC-style From header: Name &lt;email&gt; */
    public String getFromHeader() {
        String name = fromName == null ? "" : fromName.trim();
        String email = fromEmail == null ? "" : fromEmail.trim();
        if (name.isBlank()) {
            return email;
        }
        return name + " <" + email + ">";
    }
}
