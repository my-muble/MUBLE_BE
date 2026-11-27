package com.typeforsheet.backend.domain.record.service;

import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.record.dto.RecordDetailResponse;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordDetailService {
  private static final Logger log = LoggerFactory.getLogger(RecordDetailService.class);
  private final RecordReadService readService;
  private final AlbumDetailService albums;
  private final TrackDetailService tracks;

  public RecordDetailResponse getDetail(Long userId, Long recordId) {
    RecordDetailResponse local = readService.read(userId, recordId);
    RecordDetailResponse.Music music = local.music();
    try {
      if (music.type() == MusicType.ALBUM) {
        var album = albums.getAlbum(music.musicId());
        music =
            new RecordDetailResponse.Music(
                music.musicId(),
                music.type(),
                album.title(),
                album.artistName(),
                album.coverImageUrl());
      } else {
        var track = tracks.getTrack(music.musicId());
        music =
            new RecordDetailResponse.Music(
                music.musicId(),
                music.type(),
                track.title(),
                track.artistName(),
                track.coverImageUrl());
      }
    } catch (BusinessException exception) {
      if (exception.getErrorCode() != ErrorCode.MUSIC_CATALOG_UNAVAILABLE
          && exception.getErrorCode() != ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND) throw exception;
      log.warn("Music metadata unavailable for record {}: {}", recordId, exception.getErrorCode());
    }
    return new RecordDetailResponse(
        local.recordId(),
        local.authorId(),
        local.authorNickname(),
        local.authorProfileImageUrl(),
        music,
        local.rating(),
        local.content(),
        local.isPrivate(),
        local.createdAt(),
        local.updatedAt(),
        local.isOwner());
  }
}
