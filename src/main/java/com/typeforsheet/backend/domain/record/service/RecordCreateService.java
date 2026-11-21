package com.typeforsheet.backend.domain.record.service;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.dto.CreateRecordRequest;
import com.typeforsheet.backend.domain.record.dto.CreateRecordResponse;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.entity.RecordCreateRequest;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.record.repository.RecordCreateRequestRepository;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RecordCreateService {
  private final UserRepository users;
  private final MusicItemRepository musicItems;
  private final MusicRecordRepository records;
  private final RecordCreateRequestRepository requests;
  private final JsonMapper mapper;
  private final TransactionTemplate transaction;

  public RecordCreateService(
      UserRepository users,
      MusicItemRepository musicItems,
      MusicRecordRepository records,
      RecordCreateRequestRepository requests,
      JsonMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.users = users;
    this.musicItems = musicItems;
    this.records = records;
    this.requests = requests;
    this.mapper = mapper;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public CreateRecordResponse create(Long userId, String key, CreateRecordRequest request) {
    byte[] keyBytes = validateKey(key);
    NormalizedRequest normalized = normalize(request);
    byte[] hash = hash(mapper.writeValueAsBytes(normalized));
    try {
      return transaction.execute(status -> createInTransaction(userId, keyBytes, hash, normalized));
    } catch (DataIntegrityViolationException conflict) {
      // A unique-key conflict must be examined in a fresh transaction after rollback.
      CreateRecordResponse existing;
      try {
        existing =
            transaction.execute(
                status -> {
                  users
                      .findByIdForUpdate(userId)
                      .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                  return requests
                      .findByUser_IdAndIdempotencyKey(userId, keyBytes)
                      .map(value -> replay(value, hash))
                      .orElse(null);
                });
      } catch (PessimisticLockingFailureException timeout) {
        throw new BusinessException(ErrorCode.RECORD_REQUEST_RETRY, timeout);
      }
      if (existing != null) return existing;
      throw conflict;
    } catch (PessimisticLockingFailureException timeout) {
      throw new BusinessException(ErrorCode.RECORD_REQUEST_RETRY, timeout);
    }
  }

  private CreateRecordResponse createInTransaction(
      Long userId, byte[] key, byte[] hash, NormalizedRequest input) {
    User user =
        users
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    RecordCreateRequest previous =
        requests.findByUser_IdAndIdempotencyKey(userId, key).orElse(null);
    if (previous != null) {
      if (previous.getExpiresAt().isAfter(LocalDateTime.now())) return replay(previous, hash);
      requests.delete(previous);
      requests.flush();
    }
    MusicItem music =
        musicItems
            .findById(input.musicId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MUSIC_ITEM_NOT_FOUND));
    MusicRecord record =
        records.saveAndFlush(
            new MusicRecord(
                user, music, input.ratingHalfSteps(), input.content(), input.isPrivate()));
    CreateRecordResponse response = CreateRecordResponse.from(record);
    requests.saveAndFlush(
        new RecordCreateRequest(
            user, key, hash, record.getId(), mapper.writeValueAsString(response)));
    return response;
  }

  private CreateRecordResponse replay(RecordCreateRequest previous, byte[] hash) {
    if (!Arrays.equals(previous.getRequestHash(), hash))
      throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    if (previous.getRecordId() == null)
      throw new BusinessException(ErrorCode.RECORD_ALREADY_DELETED);
    return mapper.readValue(previous.getResponseSnapshot(), CreateRecordResponse.class);
  }

  private byte[] validateKey(String key) {
    if (key == null
        || key.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
        || key.codePointCount(0, key.length()) > 128
        || key.codePoints().anyMatch(Character::isISOControl)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 512) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    return bytes;
  }

  private NormalizedRequest normalize(CreateRecordRequest request) {
    if (request == null
        || request.musicId() == null
        || request.musicId() <= 0
        || request.isPrivate() == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    Byte halfSteps = null;
    if (request.rating() != null) {
      try {
        int value = request.rating().multiply(BigDecimal.valueOf(2)).intValueExact();
        if (value < 1 || value > 10) throw new ArithmeticException();
        halfSteps = (byte) value;
      } catch (ArithmeticException invalid) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid);
      }
    }
    String content = request.content();
    if (content != null
        && content
            .codePoints()
            .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) content = null;
    if (content != null && content.codePointCount(0, content.length()) > 500)
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    if (halfSteps == null && content == null)
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    return new NormalizedRequest(request.musicId(), halfSteps, content, request.isPrivate());
  }

  private byte[] hash(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record NormalizedRequest(
      Long musicId, Byte ratingHalfSteps, String content, boolean isPrivate) {}
}
