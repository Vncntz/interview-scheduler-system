package com.company.iss.booking.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
                SELECT b
                FROM Booking b
                WHERE
                    LOWER(CONCAT(
                        b.applicant.firstName,
                        ' ',
                        COALESCE(b.applicant.middleName, ''),
                        ' ',
                        b.applicant.lastName
                    )) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(b.bookingReference) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Booking> search(String keyword);

    List<Booking> findByApplicant(Applicant applicant);

    List<Booking> findBySchedule(Schedule schedule);

    List<Booking> findByStatus(BookingStatus status);

    Optional<Booking> findFirstByApplicantAndStatusIn(Applicant applicant, List<BookingStatus> statuses);

    Long countByStatus(BookingStatus status);

    Optional<Booking> findByApplicantAndSchedule(Applicant applicant, Schedule schedule);
}