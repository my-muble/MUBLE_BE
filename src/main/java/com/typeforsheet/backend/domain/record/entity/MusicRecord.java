package com.typeforsheet.backend.domain.record.entity;

import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "music_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "music_item_id", nullable = false, updatable = false)
  private MusicItem musicItem;

  @Column(name = "rating_half_steps")
  private Byte ratingHalfSteps;

  @Column(length = 500)
  private String content;

  @Column(name = "is_private", nullable = false)
  private boolean isPrivate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public MusicRecord(
      User user, MusicItem musicItem, Byte ratingHalfSteps, String content, boolean isPrivate) {
    this.user = Objects.requireNonNull(user);
    this.musicItem = Objects.requireNonNull(musicItem);
    this.ratingHalfSteps = ratingHalfSteps;
    this.content = content;
    this.isPrivate = isPrivate;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = createdAt;
  }
}
