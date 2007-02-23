/*
 * LoginHandler.java
 *
 * Created on January 29, 2007, 8:25 PM
 *
 */

package org.opends.ui.web.login;

/**
 */
public class LoginHandler {
    
    private String userName = null;
    private String password = null;
    private String errorType = null;
    private String errorSummary = null;
    private String errorDetail = null;
    
    /** Creates a new instance of LoginHandler */
    public LoginHandler() {
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getUserName() {
        return this.userName;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getPassword() {
        return this.password;
    }
    
    public String getErrorType() {
        return this.errorType;
    }
    
    public String getErrorDetail() {
        return this.errorDetail;
    }
    
    public String getErrorSummary() {
        return this.errorSummary;
    }
    
    public String login() {
        String r = null;
        if (this.userName == null || this.password == null) {
            this.errorDetail = "You must provide a user name and password";
            this.errorSummary = "Log In Failure";
            this.errorType = "Error";
            this.password = null;
            r = "failure";            
        } else {
            this.errorDetail = null;
            this.errorSummary = null;
            this.errorType = null;
            this.password = null;
            r = "success";
        }
        return r;
    }
    
    
    
    
    
}
