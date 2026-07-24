package io.softa.starter.permission.spi;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionInfoTest {

    /** Super-admin role code — the "SUPER_ADMIN" literal (the engine owns this
     *  contract; user-starter's RoleConstant.CODE_SUPER_ADMIN mirrors the same
     *  string). Kept local so this test carries no user-starter dependency. */
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    @Test
    void isSuperAdmin_holderRole_returnsTrue() {
        PermissionInfo pi = PermissionInfo.builder()
                .roleCodes(Set.of(SUPER_ADMIN))
                .build();
        assertThat(pi.isSuperAdmin()).isTrue();
    }

    @Test
    void isSuperAdmin_regularRole_returnsFalse() {
        PermissionInfo pi = PermissionInfo.builder()
                .roleCodes(Set.of("HR_MANAGER"))
                .build();
        assertThat(pi.isSuperAdmin()).isFalse();
    }

    @Test
    void isSuperAdmin_emptyRoleCodes_returnsFalse() {
        PermissionInfo pi = PermissionInfo.builder().roleCodes(Set.of()).build();
        assertThat(pi.isSuperAdmin()).isFalse();
    }

    @Test
    void isSuperAdmin_nullRoleCodes_returnsFalseWithoutThrowing() {
        PermissionInfo pi = new PermissionInfo();
        assertThat(pi.isSuperAdmin()).isFalse();
    }

    @Test
    void isSuperAdmin_static_nullPi_returnsFalse() {
        assertThat(PermissionInfo.isSuperAdmin(null)).isFalse();
    }

    @Test
    void isSuperAdmin_static_delegatesToInstance() {
        PermissionInfo pi = PermissionInfo.builder()
                .roleCodes(Set.of(SUPER_ADMIN))
                .build();
        assertThat(PermissionInfo.isSuperAdmin(pi)).isTrue();
    }
}
