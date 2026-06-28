package com.questionmark.simpropos.session;

import com.questionmark.simpropos.model.ShiftDto;

import javax.inject.Singleton;

@Singleton
public class AppSession {

    private long   userId;
    private String userName;
    private String userRole;
    private boolean loggedIn;

    // config values loaded once at startup
    private int warehouseId;
    private int defaultBpId;
    private int outletId;
    private int terminalId;

    // active shift — null when no shift is open
    private ShiftDto activeShift;

    public void login(long id, String name, String role) {
        this.userId   = id;
        this.userName = name;
        this.userRole = role;
        this.loggedIn = true;
    }

    public void logout() {
        this.loggedIn = false;
        this.userId   = 0;
        this.userName = null;
        this.userRole = null;
    }

    public boolean isLoggedIn()  { return loggedIn; }
    public long    getUserId()   { return userId; }
    public String  getUserName() { return userName; }
    public String  getUserRole() { return userRole; }

    public int getWarehouseId() { return warehouseId; }
    public int getDefaultBpId() { return defaultBpId; }
    public int getOutletId()    { return outletId; }
    public int getTerminalId()  { return terminalId; }

    public void initFromConfig(int warehouseId, int defaultBpId, int outletId, int terminalId) {
        this.warehouseId = warehouseId;
        this.defaultBpId = defaultBpId;
        this.outletId    = outletId;
        this.terminalId  = terminalId;
    }

    public ShiftDto getActiveShift()          { return activeShift; }
    public void     setActiveShift(ShiftDto s){ this.activeShift = s; }
    public boolean  hasActiveShift()          { return activeShift != null; }
    public void     clearShift()              { this.activeShift = null; }
}
