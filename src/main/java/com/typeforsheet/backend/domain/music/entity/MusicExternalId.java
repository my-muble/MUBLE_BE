package com.typeforsheet.backend.domain.music.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "music_external_ids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicExternalId {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "music_item_id", nullable = false)
  private MusicItem musicItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MusicProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "external_id_type", nullable = false, length = 32)
  private ExternalIdType externalIdType;

  @Column(name = "external_id", nullable = false, length = 255)
  private String externalId;

  public MusicExternalId(
      MusicItem musicItem,
      MusicProvider provider,
      ExternalIdType externalIdType,
      String externalId) {
    this.musicItem = Objects.requireNonNull(musicItem);
    this.provider = Objects.requireNonNull(provider);
    this.externalIdType = Objects.requireNonNull(externalIdType);
    this.externalId = Objects.requireNonNull(externalId);
    if (externalId.isBlank()
        || externalId.length() > 255
        || externalIdType.getProvider() != provider
        || externalIdType.getMusicType() != musicItem.getType()) {
      throw new IllegalArgumentException("Invalid music external ID mapping");
    }
  }
}
