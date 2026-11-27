package com.typeforsheet.backend.domain.record.service;

import com.typeforsheet.backend.domain.record.dto.RecordDetailResponse;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordReadService {
  private final MusicRecordRepository records;
  private final UserRepository users;

  @Transactional(readOnly = true)
  public RecordDetailResponse read(Long userId, Long recordId) {
    if (recordId == null || recordId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    MusicRecord record =
        records
            .findWithUserAndMusicItemById(recordId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    boolean owner = record.getUser().getId().equals(userId);
    if (record.isPrivate() && !owner) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
    return new RecordDetailResponse(
        record.getId(),
        record.getUser().getId(),
        record.getUser().getNickname(),
        null,
        new RecordDetailResponse.Music(
            record.getMusicItem().getId(), record.getMusicItem().getType(), null, null, null),
        record.getRatingHalfSteps() == null
            ? null
            : BigDecimal.valueOf(record.getRatingHalfSteps()).divide(BigDecimal.valueOf(2)),
        record.getContent(),
        record.isPrivate(),
        record.getCreatedAt(),
        record.getUpdatedAt(),
        owner);
  }
}
