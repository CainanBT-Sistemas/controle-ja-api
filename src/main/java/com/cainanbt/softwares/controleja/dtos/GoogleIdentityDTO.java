package com.cainanbt.softwares.controleja.dtos;

/**
 * Identidade extraida de um idToken Google ja validado.
 */
public record GoogleIdentityDTO(
        String subject,
        String email,
        String displayName,
        String photoUrl
) {
}
