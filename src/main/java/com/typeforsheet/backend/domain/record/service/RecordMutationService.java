package com.typeforsheet.backend.domain.record.service;

import com.typeforsheet.backend.domain.record.dto.CreateRecordResponse;
import com.typeforsheet.backend.domain.record.dto.UpdateRecordRequest;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordMutationService {
  private final UserRepository users;
  private final MusicRecordRepository records;

  @Transactional
  public CreateRecordResponse update(Long userId, Long recordId, UpdateRecordRequest request) {
    MusicRecord record = ownedRecord(userId, recordId);
    if (request == null || (request.privatePresent() && request.isPrivate() == null))
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    Byte rating =
        request.ratingPresent()
            ? RecordValidation.rating(request.rating())
            : record.getRatingHalfSteps();
    String content =
        request.contentPresent()
            ? RecordValidation.content(request.content())
            : record.getContent();
    boolean isPrivate = request.privatePresent() ? request.isPrivate() : record.isPrivate();
    RecordValidation.requireContent(rating, content);
    record.update(rating, content, isPrivate);
    records.flush();
    return CreateRecordResponse.from(record);
  }

  @Transactional
  public void delete(Long userId, Long recordId) {
    MusicRecord record = ownedRecord(userId, recordId);
    records.delete(record);
    records.flush();
  }

  private MusicRecord ownedRecord(Long userId, Long recordId) {
    if (recordId == null || recordId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    users
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    MusicRecord record =
        records
            .findByIdForUpdate(recordId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    if (!record.getUser().getId().equals(userId)) {
      throw new BusinessException(
          record.isPrivate() ? ErrorCode.RECORD_NOT_FOUND : ErrorCode.ACCESS_DENIED);
    }
    return record;
  }
}
