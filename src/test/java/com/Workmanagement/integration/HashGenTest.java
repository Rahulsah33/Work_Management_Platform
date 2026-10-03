package com.Workmanagement.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

public class HashGenTest extends BaseIntegrationTest {

    @Autowired
    private PasswordEncoder encoder;

    @Test
    void printHashes() {
        System.out.println("=== START HASHES ===");
        System.out.println("ADMIN_HASH=" + encoder.encode("AdminPassword123!"));
        System.out.println("MANAGER_HASH=" + encoder.encode("ManagerPassword123!"));
        System.out.println("=== END HASHES ===");
    }
}

