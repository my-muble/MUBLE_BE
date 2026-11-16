package com.typeforsheet.backend.domain.music.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "music_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MusicType type;

  public MusicItem(MusicType type) {
    this.type = Objects.requireNonNull(type);
  }
}
