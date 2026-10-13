create unique index worldline_hosted_tokenization_by_id
    on worldline_hosted_tokenization (hosted_tokenization_id);

alter table worldline_hosted_tokenization
    drop column hosted_tokenization_url,
    drop column partial_redirect_url;
