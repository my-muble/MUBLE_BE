package com.typeforsheet.backend.domain.record.entity;

import com.typeforsheet.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "record_create_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordCreateRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "idempotency_key", nullable = false, length = 512, updatable = false)
  private byte[] idempotencyKey;

  @Column(
      name = "request_hash",
      nullable = false,
      columnDefinition = "BINARY(32)",
      updatable = false)
  private byte[] requestHash;

  @Column(name = "record_id", updatable = false)
  private Long recordId;

  @Column(
      name = "response_snapshot",
      nullable = false,
      columnDefinition = "TEXT",
      updatable = false)
  private String responseSnapshot;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private LocalDateTime expiresAt;

  public RecordCreateRequest(
      User user,
      byte[] idempotencyKey,
      byte[] requestHash,
      Long recordId,
      String responseSnapshot) {
    this.user = Objects.requireNonNull(user);
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey).clone();
    this.requestHash = Objects.requireNonNull(requestHash).clone();
    this.recordId = Objects.requireNonNull(recordId);
    this.responseSnapshot = Objects.requireNonNull(responseSnapshot);
    this.createdAt = LocalDateTime.now();
    this.expiresAt = createdAt.plusDays(30);
  }
}
