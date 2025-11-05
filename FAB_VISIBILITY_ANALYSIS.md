# fabAddGlobal (FAB Create Activity) Visibility Analysis

## Issue Summary
The FAB (Floating Action Button) for creating activities doesn't appear on first login but appears after logout and login again.

## 1. FAB INITIALIZATION AND VISIBILITY CONTROL

### A. FAB Initialization (MainActivity.java - Lines 127-128)
The FAB is initialized in onCreate:
- fabGlobal = findViewById(R.id.fabAddGlobal);
- Reference stored in fabGlobal member variable

### B. FAB Visibility Logic (MainActivity.java - Lines 199-210)
Controlled by OnDestinationChangedListener attached to NavController:

Key logic:
- FAB only shows on CalendarFragment or ActivitiesListFragment
- Visibility depends on TWO conditions:
  1. Current destination must be Calendar or Activities List
  2. currentUserRole.canInteractWithActivities() must return true

## 2. ROLE SYSTEM INITIALIZATION

### A. Role Initialization (MainActivity.java - Line 160)
initializeRoleSystem() is called in onCreate

### B. Role Loading Process (MainActivity.java - Lines 219-244)
- roleManager.loadUserRole() is ASYNCHRONOUS (loads from Firebase)
- currentUserRole starts as UserRole.VISUALIZADOR (default)
- FAB visibility is updated AFTER role loads from Firebase
- Uses runOnUiThread to update FAB on main thread

### C. Critical Issue
- OnDestinationChangedListener fires BEFORE role loads from Firebase
- At first navigation to Calendar/Activities, currentUserRole is still VISUALIZADOR
- FAB gets hidden and NEVER shows up
- After logout/login, role is properly cached so it works on second login

## 3. FRAGMENTS CONTROLLING FAB

### A. CalendarFragment
File: app/src/main/java/com/centroalerce/ui/CalendarFragment.java
- FAB shows when active (if user has permissions)
- Does NOT directly control FAB - MainActivity controls it

### B. ActivitiesListFragment  
File: app/src/main/java/com/centroalerce/ui/ActivitiesListFragment.java
- FAB shows when active (if user has permissions)
- Does NOT directly control FAB - MainActivity controls it

Note: Both fragments initialize role system independently but FAB control is ONLY in MainActivity

## 4. ROOT CAUSE - TIMING ISSUE

Flow:
1. App starts -> fabGlobal initialized, currentUserRole = VISUALIZADOR
2. initializeRoleSystem() starts async Firebase load
3. User navigates to CalendarFragment
4. OnDestinationChangedListener checks: currentUserRole.canInteractWithActivities()
5. PROBLEM: currentUserRole is still VISUALIZADOR, so fabGlobal.hide() executes
6. Firebase role response arrives later, updates currentUserRole
7. BUT OnDestinationChangedListener is NOT triggered again
8. FAB remains hidden

Solution after logout/login:
- roleManager.clearRole() clears the cached role
- On next login, role is loaded first
- When navigating to Calendar, currentUserRole is correct
- FAB shows properly

## 5. KEY FILE LOCATIONS

MainActivity: app/src/main/java/com/centroalerce/gestion/MainActivity.java
- Lines 127-128: FAB initialization
- Lines 163-211: OnDestinationChangedListener with FAB logic
- Lines 219-244: initializeRoleSystem() with async role loading
- Lines 348-370: onResume() also updates FAB visibility

RoleManager: app/src/main/java/com/centroalerce/gestion/utils/RoleManager.java
- Lines 53-89: loadUserRole() - ASYNCHRONOUS Firebase load
- Lines 184-189: clearRole() - called on logout

UserRole: app/src/main/java/com/centroalerce/gestion/utils/UserRole.java
- Lines 122-124: canInteractWithActivities() - determines if FAB should show

SettingsFragment: app/src/main/java/com/centroalerce/ui/SettingsFragment.java
- Lines 238-253: Logout logic that calls roleManager.clearRole()

## 6. RECOMMENDED SOLUTIONS

Option A - Ensure role loads before navigation:
- Wait for role to load before allowing navigation to Calendar/Activities
- Prevents the timing issue entirely

Option B - Re-check role in OnDestinationChangedListener:
- Keep checking if role has updated since last check
- Trigger FAB show when role finally loads

Option C - Make role state observable:
- Use subscribeToRoleChanges() to listen for role updates
- Update FAB whenever role changes

Option D - Default FAB to visible:
- Show FAB by default, hide only if confirmed VISUALIZADOR
- Reverse the current logic

