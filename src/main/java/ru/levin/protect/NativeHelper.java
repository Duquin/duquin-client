package ru.levin.protect;


import ru.levin.manager.Manager;
import ru.levin.protect.loader.NativeProfile;

public class NativeHelper {
    public static void setProfile() {
        Manager.USER_PROFILE = new UserProfile(
                "Duquin",
                "Owner",
                "Бессрочно"
        );
    }
}
