package com.example.shop;

public final class NoSuchStory extends RuntimeException {

  private static final long serialVersionUID = 1L;

  NoSuchStory(String slug) {
    super("this shop tells no story called " + slug);
  }
}
