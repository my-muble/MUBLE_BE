package com.typeforsheet.backend.domain.record.service;

import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.dto.MusicRecordListResponse;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordListRepository;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MusicRecordListService {
  private final MusicRecordListRepository records;
  private final MusicItemRepository musicItems;
  private final UserRepository users;

  @Transactional(readOnly = true)
  public MusicRecordListResponse<MusicRecordListResponse.Mine> getMine(
      Long userId, Long musicId, int limit, LocalDateTime cursorCreatedAt, Long cursorId) {
    validate(userId, musicId, limit, cursorCreatedAt, cursorId);
    var found = records.findMine(musicId, userId, cursorCreatedAt, cursorId, limit + 1);
    long total = records.countMine(musicId, userId);
    return page(found, total, limit, this::toMine);
  }

  @Transactional(readOnly = true)
  public MusicRecordListResponse<MusicRecordListResponse.Other> getOthers(
      Long userId, Long musicId, int limit, LocalDateTime cursorCreatedAt, Long cursorId) {
    validate(userId, musicId, limit, cursorCreatedAt, cursorId);
    var found = records.findOthers(musicId, userId, cursorCreatedAt, cursorId, limit + 1);
    long total = records.countOthers(musicId, userId);
    return page(found, total, limit, this::toOther);
  }

  private void validate(
      Long userId, Long musicId, int limit, LocalDateTime cursorCreatedAt, Long cursorId) {
    if (musicId == null
        || musicId <= 0
        || limit < 1
        || limit > 50
        || (cursorCreatedAt == null) != (cursorId == null)
        || (cursorId != null && cursorId <= 0)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    if (!users.existsById(userId)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    if (!musicItems.existsById(musicId))
      throw new BusinessException(ErrorCode.MUSIC_ITEM_NOT_FOUND);
  }

  private <T> MusicRecordListResponse<T> page(
      List<MusicRecord> found, long total, int limit, Function<MusicRecord, T> mapper) {
    boolean hasNext = found.size() > limit;
    List<MusicRecord> visible = found.subList(0, Math.min(found.size(), limit));
    MusicRecordListResponse.Cursor next = null;
    if (hasNext) {
      MusicRecord last = visible.getLast();
      next = new MusicRecordListResponse.Cursor(last.getCreatedAt(), last.getId());
    }
    return new MusicRecordListResponse<>(
        visible.stream().map(mapper).toList(), total, hasNext, next);
  }

  private MusicRecordListResponse.Mine toMine(MusicRecord record) {
    return new MusicRecordListResponse.Mine(
        record.getId(),
        rating(record),
        record.getContent(),
        record.isPrivate(),
        record.getCreatedAt());
  }

  private MusicRecordListResponse.Other toOther(MusicRecord record) {
    return new MusicRecordListResponse.Other(
        record.getId(),
        record.getUser().getId(),
        record.getUser().getNickname(),
        null,
        rating(record),
        record.getContent(),
        record.getCreatedAt());
  }

  private BigDecimal rating(MusicRecord record) {
    return record.getRatingHalfSteps() == null
        ? null
        : BigDecimal.valueOf(record.getRatingHalfSteps()).divide(BigDecimal.valueOf(2));
  }
}
