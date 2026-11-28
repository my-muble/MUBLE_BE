package com.typeforsheet.backend.domain.archive.dto;

import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Album;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.AlbumRecord;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "전체 기간 누적 앨범 기록 상세")
public record ArchiveAlbumDetailResponse(
    Album album,
    @Schema(description = "앨범 자체 기록과 수록곡 기록의 합계") long totalRecordCount,
    @Schema(description = "앨범 자체 기록 수") long albumRecordCount,
    @Schema(description = "수록곡 기록 수") long trackRecordCount,
    @Schema(description = "앨범 자체 기록 최근 최대 3건") List<AlbumRecord> albumRecords,
    @Schema(description = "앨범 수록곡 순서. 기록이 없는 수록곡도 포함") List<Track> tracks) {}
