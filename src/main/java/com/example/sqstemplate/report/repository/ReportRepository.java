package com.example.sqstemplate.report.repository;

import com.example.sqstemplate.report.domain.Report;
import com.example.sqstemplate.report.domain.ReportStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	List<Report> findAllByStatusOrderByRequestedAtDesc(ReportStatus status);

	List<Report> findAllByOrderByRequestedAtDesc();
}
