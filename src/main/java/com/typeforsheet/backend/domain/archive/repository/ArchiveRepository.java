package com.typeforsheet.backend.domain.archive.repository;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ArchiveRepository extends Repository<MusicRecord, Long> {

  boolean existsByUser_Id(Long userId);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow(
            r.id,
            m.id,
            m.type,
            r.createdAt
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<ArchiveRecordRow> findAlbumRows(@Param("userId") Long userId);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow(
            r.id, m.id, m.type, r.createdAt, r.ratingHalfSteps, r.content
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<ArchiveRecordRow> findAllDetailRows(@Param("userId") Long userId);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow(
            r.id, m.id, m.type, r.createdAt, r.ratingHalfSteps, r.content
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId AND m.id = :musicItemId AND m.type = :musicType
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<ArchiveRecordRow> findMusicRows(
      @Param("userId") Long userId,
      @Param("musicItemId") Long musicItemId,
      @Param("musicType") MusicType musicType);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow(
            r.id,
            m.id,
            m.type,
            r.createdAt
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId
          AND r.createdAt >= :startInclusive
          AND r.createdAt < :endExclusive
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<ArchiveRecordRow> findTimelineRows(
      @Param("userId") Long userId,
      @Param("startInclusive") LocalDateTime startInclusive,
      @Param("endExclusive") LocalDateTime endExclusive);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow(
            r.id, m.id, m.type, r.createdAt, r.ratingHalfSteps, r.content
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId
          AND r.createdAt >= :startInclusive
          AND r.createdAt < :endExclusive
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<ArchiveRecordRow> findDayRows(
      @Param("userId") Long userId,
      @Param("startInclusive") LocalDateTime startInclusive,
      @Param("endExclusive") LocalDateTime endExclusive);

  @Query(
      """
        SELECT new com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow(
            r.id, m.id, m.type, r.createdAt, r.ratingHalfSteps, r.content
        )
        FROM MusicRecord r
        JOIN r.musicItem m
        WHERE r.user.id = :userId AND m.id = :musicItemId AND m.type = :musicType
          AND r.createdAt >= :startInclusive AND r.createdAt < :endExclusive
        ORDER BY r.createdAt DESC, r.id DESC
        """)
  List<ArchiveRecordRow> findDayMusicRows(
      @Param("userId") Long userId,
      @Param("musicItemId") Long musicItemId,
      @Param("musicType") MusicType musicType,
      @Param("startInclusive") LocalDateTime startInclusive,
      @Param("endExclusive") LocalDateTime endExclusive);
}
