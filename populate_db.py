#!/usr/bin/env python3
"""
Populate the Instakill SQLite database (instakill.db) with configurable test data.

Usage examples:
  python populate_db.py
  python populate_db.py --users 10000 --min-posts 10 --max-posts 20 --min-comments 0 --max-comments 10 --min-likes 0 --max-likes 20
  python populate_db.py --users 500 --batch-size 5000
"""

import argparse
import datetime
import os
import random
import sqlite3
import time
import uuid
from pathlib import Path

import bcrypt
from faker import Faker


DEFAULT_PHOTO_URLS = [
    "/media/1ca075f4-ac42-4d27-b297-a525dc70b8e4.jpeg",
    "/media/8b0c93ce-8b1b-47be-ab67-d14c5ae78732.jpeg",
    "/media/989147e6-97b2-4677-a572-3a2f1f42253d.jpeg",
    "/media/b25e4cab-b2e9-4d48-9684-9bebc8533def.jpg",
    "/media/cce27c81-c7ac-4a6d-ab97-43535d84d5fa.jpeg",
    "/media/f1f81cd5-26b4-4202-80de-2521af6815a5.gif",
    "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
    "https://images.unsplash.com/photo-1447933601403-0c6688de566e",
]

DEFAULT_VIDEO_URLS = [
    "/media/2f4e4b10-308a-4d81-9d69-c6ef7565ac47.mp4",
    "/media/53d043d2-3521-47e4-a0a6-6684bbcec952.mp4",
    "/media/591c0019-ec33-4273-a08e-5379e2526f54.mp4",
    "/media/69896212-0c2c-41e4-95ef-e251c0eac804.mp4",
    "/media/87084505-b8c2-4ad1-ac5f-bbe4334f78e5.mp4",
    "/media/8a83309a-55af-4ecb-bb92-926c0057f705.mp4",
    "/media/9a35e03d-fa6d-4733-a238-dad8daf8ff7f.mp4",
    "/media/d00792f5-8d3f-4e2c-bc7d-67f6ae8e37ee.mp4",
    "/media/d33e98fe-8a73-4cd3-9a54-c30e3dfa29b8.mp4",
]


def resolve_default_db_path() -> Path:
    """Locate instakill.db whether invoked from the project root or backend/ directory."""
    candidates = [
        Path("backend/instakill.db"),
        Path("instakill.db"),
        Path(__file__).resolve().parent / "backend" / "instakill.db",
        Path(__file__).resolve().parent / "instakill.db",
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate.resolve()
    return (Path(__file__).resolve().parent / "backend" / "instakill.db").resolve()


def ensure_tables(conn: sqlite3.Connection) -> None:
    """Ensure the expected instakill.db tables exist."""
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS users (
            id char(36) not null,
            created_at timestamp not null,
            email varchar(255) not null unique,
            password_hash varchar(255) not null,
            username varchar(255) not null unique,
            primary key (id)
        );
        CREATE TABLE IF NOT EXISTS posts (
            id char(36) not null,
            author_id char(36) not null,
            comment_count bigint not null,
            content varchar(1000) not null,
            created_at timestamp not null,
            image_url varchar(2048),
            like_count bigint not null,
            updated_at timestamp not null,
            primary key (id)
        );
        CREATE TABLE IF NOT EXISTS comments (
            id char(36) not null,
            author_id char(36) not null,
            content varchar(500) not null,
            created_at timestamp not null,
            post_id char(36) not null,
            primary key (id)
        );
        CREATE TABLE IF NOT EXISTS likes (
            id char(36) not null,
            created_at timestamp not null,
            post_id char(36) not null,
            user_id char(36) not null,
            primary key (id)
        );
        """
    )


def create_indexes(conn: sqlite3.Connection) -> None:
    """Create secondary indexes after bulk loading for fast query execution."""
    conn.executescript(
        """
        CREATE INDEX IF NOT EXISTS idx_comments_post on comments (post_id);
        CREATE INDEX IF NOT EXISTS idx_posts_author on posts (author_id);
        CREATE INDEX IF NOT EXISTS idx_posts_created_at on posts (created_at);
        """
    )


def drop_indexes(conn: sqlite3.Connection) -> None:
    """Temporarily drop secondary indexes before bulk insert to speed up batch writes."""
    conn.executescript(
        """
        DROP INDEX IF EXISTS idx_comments_post;
        DROP INDEX IF EXISTS idx_posts_author;
        DROP INDEX IF EXISTS idx_posts_created_at;
        """
    )


def date_to_epoch_ms(d: datetime.date) -> int:
    """Convert a date from faker.date_between_dates() to epoch milliseconds with a random time of day."""
    random_seconds = random.randint(0, 86399)
    dt = datetime.datetime.combine(
        d, datetime.time.min, tzinfo=datetime.timezone.utc
    ) + datetime.timedelta(seconds=random_seconds)
    return int(dt.timestamp() * 1000)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Populate instakill.db with configurable synthetic test data."
    )
    parser.add_argument(
        "--db",
        type=Path,
        default=resolve_default_db_path(),
        help="Path to the SQLite database file (default: backend/instakill.db)",
    )
    parser.add_argument(
        "--users",
        type=int,
        default=10000,
        help="Total number of random users to generate (default: 10000)",
    )
    parser.add_argument(
        "--min-posts",
        type=int,
        default=10,
        help="Minimum posts per user (default: 10)",
    )
    parser.add_argument(
        "--max-posts",
        type=int,
        default=20,
        help="Maximum posts per user (default: 20)",
    )
    parser.add_argument(
        "--min-comments",
        type=int,
        default=0,
        help="Minimum comments per post (default: 0)",
    )
    parser.add_argument(
        "--max-comments",
        type=int,
        default=10,
        help="Maximum comments per post (default: 10)",
    )
    parser.add_argument(
        "--min-likes",
        type=int,
        default=0,
        help="Minimum likes per post (default: 0)",
    )
    parser.add_argument(
        "--max-likes",
        type=int,
        default=20,
        help="Maximum likes per post (default: 20)",
    )
    parser.add_argument(
        "--max-words",
        type=int,
        default=10,
        help="Maximum random words per post/comment (default: 10)",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=10000,
        help="Batch size for bulk SQLite inserts (default: 10000)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Optional random seed for reproducible data generation",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.seed is not None:
        random.seed(args.seed)
        Faker.seed(args.seed)

    fake = Faker()
    db_path: Path = args.db.resolve()
    if not db_path.parent.exists():
        os.makedirs(db_path.parent, exist_ok=True)

    print(f"Connecting to SQLite database: {db_path}")
    start_time = time.perf_counter()

    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA journal_mode = WAL;")
    conn.execute("PRAGMA synchronous = OFF;")
    conn.execute("PRAGMA temp_store = MEMORY;")
    conn.execute("PRAGMA cache_size = -64000;")

    ensure_tables(conn)
    drop_indexes(conn)

    # Hash "password" using bcrypt (Spring Security BCryptPasswordEncoder compatible $2a$ prefix)
    charlie_password_hash = bcrypt.hashpw(
        b"password", bcrypt.gensalt(rounds=10, prefix=b"2a")
    ).decode("utf-8")

    today = datetime.date.today()
    one_week_ago = today - datetime.timedelta(days=7)
    now_ms = int(datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000)

    # Build a word pool and past-week date pool via Faker
    word_pool = fake.words(nb=500, unique=False)
    past_week_dates = [
        fake.date_between_dates(date_start=one_week_ago, date_end=today)
        for _ in range(512)
    ]

    def random_text(max_words: int) -> str:
        count = random.randint(1, max(1, max_words))
        words = random.choices(word_pool, k=count)
        sentence = " ".join(words)
        return sentence.capitalize()

    def random_past_week_ms() -> int:
        chosen_date = random.choice(past_week_dates)
        return date_to_epoch_ms(chosen_date)

    # Use a single transaction for maximum bulk insert speed
    print("Cleaning existing comments, likes, posts, and non-(alice, bob) users...")
    with conn:
        conn.execute("DELETE FROM comments;")
        conn.execute("DELETE FROM likes;")
        conn.execute("DELETE FROM posts;")
        conn.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob');")

        existing_users = dict(
            conn.execute("SELECT username, id FROM users WHERE username IN ('alice', 'bob');").fetchall()
        )

        # Ensure alice and bob exist even on a fresh database
        for reserved_name in ("alice", "bob"):
            if reserved_name not in existing_users:
                uid = str(uuid.uuid4())
                conn.execute(
                    "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?, ?, ?, ?, ?);",
                    (uid, now_ms, f"{reserved_name}@example.com", charlie_password_hash, reserved_name),
                )
                existing_users[reserved_name] = uid

        # Insert charlie with bcrypt-encoded password "password"
        charlie_id = str(uuid.uuid4())
        conn.execute(
            "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?, ?, ?, ?, ?);",
            (charlie_id, now_ms, "charlie@example.com", charlie_password_hash, "charlie"),
        )
        existing_users["charlie"] = charlie_id

        print("Preserved 'alice' and 'bob'; added user 'charlie' (password='password').")

        # Step 2: Generate and batch-insert random users with unique usernames from Faker
        used_usernames = {"alice", "bob", "charlie"}
        all_user_ids: list[str] = list(existing_users.values())
        user_batch: list[tuple[str, int, str, str, str]] = []

        print(f"Generating and inserting {args.users:,} users in batches of {args.batch_size:,}...")
        for i in range(args.users):
            base_username = fake.user_name()
            username = base_username
            suffix = 1
            while username.lower() in used_usernames:
                username = f"{base_username}_{i}_{suffix}"
                suffix += 1
            used_usernames.add(username.lower())

            uid = str(uuid.uuid4())
            email = f"{username.lower()}@example.com"
            created_ms = random_past_week_ms()

            all_user_ids.append(uid)
            user_batch.append((uid, created_ms, email, charlie_password_hash, username))

            if len(user_batch) >= args.batch_size:
                conn.executemany(
                    "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?, ?, ?, ?, ?);",
                    user_batch,
                )
                user_batch.clear()

        if user_batch:
            conn.executemany(
                "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?, ?, ?, ?, ?);",
                user_batch,
            )
            user_batch.clear()

        total_users_count = len(all_user_ids)
        max_possible_likes = min(args.max_likes, total_users_count)
        min_possible_likes = min(args.min_likes, max_possible_likes)

        # Step 3: Generate posts, comments, and likes for every user and execute in batches
        print(
            f"Generating posts ({args.min_posts}-{args.max_posts}/user), "
            f"comments ({args.min_comments}-{args.max_comments}/post), and "
            f"likes ({min_possible_likes}-{max_possible_likes}/post)..."
        )

        post_batch: list[tuple[str, str, int, str, int, str, int, int]] = []
        comment_batch: list[tuple[str, str, str, int, str]] = []
        like_batch: list[tuple[str, int, str, str]] = []

        total_posts_inserted = 0
        total_comments_inserted = 0
        total_likes_inserted = 0

        def flush_batches() -> None:
            nonlocal total_posts_inserted, total_comments_inserted, total_likes_inserted
            if post_batch:
                conn.executemany(
                    """
                    INSERT INTO posts (
                        id, author_id, comment_count, content, created_at, image_url, like_count, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                    """,
                    post_batch,
                )
                total_posts_inserted += len(post_batch)
                post_batch.clear()

            if comment_batch:
                conn.executemany(
                    """
                    INSERT INTO comments (
                        id, author_id, content, created_at, post_id
                    ) VALUES (?, ?, ?, ?, ?);
                    """,
                    comment_batch,
                )
                total_comments_inserted += len(comment_batch)
                comment_batch.clear()

            if like_batch:
                conn.executemany(
                    """
                    INSERT INTO likes (
                        id, created_at, post_id, user_id
                    ) VALUES (?, ?, ?, ?);
                    """,
                    like_batch,
                )
                total_likes_inserted += len(like_batch)
                like_batch.clear()

        for user_id in all_user_ids:
            num_posts = random.randint(args.min_posts, args.max_posts)
            for _ in range(num_posts):
                post_id = str(uuid.uuid4())
                post_created_ms = random_past_week_ms()
                content = random_text(args.max_words)

                # Random media type: photo or video
                if random.random() < 0.5:
                    image_url = random.choice(DEFAULT_PHOTO_URLS)
                else:
                    image_url = random.choice(DEFAULT_VIDEO_URLS)

                num_comments = random.randint(args.min_comments, args.max_comments)
                num_likes = random.randint(min_possible_likes, max_possible_likes)

                post_batch.append(
                    (
                        post_id,
                        user_id,
                        num_comments,
                        content,
                        post_created_ms,
                        image_url,
                        num_likes,
                        post_created_ms,
                    )
                )

                # Generate comments for this post
                for _ in range(num_comments):
                    comment_id = str(uuid.uuid4())
                    comment_author_id = random.choice(all_user_ids)
                    comment_content = random_text(args.max_words)
                    comment_created_ms = max(post_created_ms, random_past_week_ms())
                    comment_batch.append(
                        (
                            comment_id,
                            comment_author_id,
                            comment_content,
                            comment_created_ms,
                            post_id,
                        )
                    )

                # Generate likes for this post (unique user_ids per post)
                if num_likes > 0:
                    liker_ids = random.sample(all_user_ids, k=num_likes)
                    for liker_id in liker_ids:
                        like_id = str(uuid.uuid4())
                        like_created_ms = max(post_created_ms, random_past_week_ms())
                        like_batch.append((like_id, like_created_ms, post_id, liker_id))

                if (
                    len(post_batch) >= args.batch_size
                    or len(comment_batch) >= args.batch_size * 2
                    or len(like_batch) >= args.batch_size * 2
                ):
                    flush_batches()

        flush_batches()

    print("Rebuilding indexes...")
    create_indexes(conn)
    conn.execute("PRAGMA synchronous = NORMAL;")
    conn.close()

    elapsed = time.perf_counter() - start_time
    print(
        f"Done in {elapsed:.2f}s! "
        f"Users: {total_users_count:,} (including alice, bob, charlie) | "
        f"Posts: {total_posts_inserted:,} | "
        f"Comments: {total_comments_inserted:,} | "
        f"Likes: {total_likes_inserted:,}"
    )


if __name__ == "__main__":
    main()
