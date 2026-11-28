package com.typeforsheet.backend.domain.archive.dto;

import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Album;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.AlbumRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "전체 기간 앨범 자체 기록 전체보기")
public record ArchiveAlbumRecordsResponse(
    Album album,
    @Schema(description = "앨범 자체 기록과 수록곡 기록의 합계") long totalRecordCount,
    @Schema(description = "앨범 자체 기록 수") long albumRecordCount,
    @Schema(description = "앨범 자체 기록 전체, 최초 작성 시각 최신순") List<AlbumRecord> albumRecords) {}
