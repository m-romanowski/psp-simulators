update stripe_account
set capture_window = 'PT168H'
where not (case
               when capture_window ~
                    '^PT([0-9]{1,5}H)?([0-9]{1,2}M)?([0-9]{1,2}(\.[0-9]{1,9})?S)?$'
                   and capture_window ~ '[1-9]'
                   then capture_window::interval <= interval '365 days'
               else false
           end);

update stripe_account
set webhook_timestamp_age = 'PT0S'
where not (case
               when webhook_timestamp_age ~
                    '^PT([0-9]{1,5}H)?([0-9]{1,2}M)?([0-9]{1,2}(\.[0-9]{1,9})?S)?$'
                   and webhook_timestamp_age ~ '[0-9]'
                   then webhook_timestamp_age::interval <= interval '365 days'
               else false
           end);

alter table stripe_account
    drop constraint stripe_account_capture_window_is_a_duration,
    drop constraint stripe_account_webhook_timestamp_age_is_a_duration,
    add constraint stripe_account_capture_window_is_a_span_stripe_reads
        check (case
                   when capture_window ~
                        '^PT([0-9]{1,5}H)?([0-9]{1,2}M)?([0-9]{1,2}(\.[0-9]{1,9})?S)?$'
                       and capture_window ~ '[1-9]'
                       then capture_window::interval <= interval '365 days'
                   else false
               end),
    add constraint stripe_account_webhook_timestamp_age_is_a_span_stripe_reads
        check (case
                   when webhook_timestamp_age ~
                        '^PT([0-9]{1,5}H)?([0-9]{1,2}M)?([0-9]{1,2}(\.[0-9]{1,9})?S)?$'
                       and webhook_timestamp_age ~ '[0-9]'
                       then webhook_timestamp_age::interval <= interval '365 days'
                   else false
               end);
