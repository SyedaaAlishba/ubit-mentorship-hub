package com.ubit.mentorshiphub.mentorship.dto;

import com.ubit.mentorshiphub.user.model.AppRole;

/**
 * The safe public identity of one participant in a request or mentorship.
 *
 * <p>Deliberately narrow: a display name, the role (which is not secret - it is
 * shown in the directory) and an avatar reference. Email, Google identity,
 * verification evidence and moderation state are never included.
 */
public record MentorshipParticipantResponse(
        String userId,
        String name,
        AppRole role,
        String avatarGridFsId
) {
}
