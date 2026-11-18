#!/usr/bin/env python3
"""
Populate the Instakill PostgreSQL database with configurable test data.

Usage examples:
  python populate_db.py
  python populate_db.py --users 10000 --min-posts 10 --max-posts 20 --min-comments 0 --max-comments 10 --min-likes 0 --max-likes 20
  python populate_db.py --users 500 --batch-size 5000
  python populate_db.py --host localhost --port 5432 --db instakill --user postgres --password postgres
"""

import argparse
import datetime
import os
import random
import time
import uuid
from typing import Any

import bcrypt
from faker import Faker

try:
    import psycopg  # type: ignore

    PG_DRIVER = "psycopg"
except ImportError:
    try:
        import psycopg2  # type: ignore
        import psycopg2.extras  # type: ignore

        PG_DRIVER = "psycopg2"
    except ImportError as exc:
        raise SystemExit(
            "Error: Neither 'psycopg' nor 'psycopg2' is installed.\n"
            'Install one with: pip install "psycopg[binary]"  (or pip install psycopg2-binary)'
        ) from exc


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


def connect_postgres(args: argparse.Namespace) -> Any:
    """Connect to PostgreSQL, creating the target database first if it does not exist."""
    if args.db_url:
        if PG_DRIVER == "psycopg":
            return psycopg.connect(args.db_url)
        return psycopg2.connect(args.db_url)

    conn_kwargs = {
        "host": args.host,
        "port": args.port,
        "dbname": args.db,
        "user": args.user,
        "password": args.password,
    }

    try:
        if PG_DRIVER == "psycopg":
            return psycopg.connect(**conn_kwargs)
        return psycopg2.connect(**conn_kwargs)
    except Exception as exc:
        if f'database "{args.db}" does not exist' not in str(exc).lower():
            raise
        print(f"Database '{args.db}' does not exist yet; creating it...")
        admin_kwargs = {**conn_kwargs, "dbname": "postgres"}
        if PG_DRIVER == "psycopg":
            with psycopg.connect(**admin_kwargs, autocommit=True) as admin_conn:
                with admin_conn.cursor() as cur:
                    cur.execute(f'CREATE DATABASE "{args.db}";')
            return psycopg.connect(**conn_kwargs)
        else:
            admin_conn = psycopg2.connect(**admin_kwargs)
            admin_conn.autocommit = True
            try:
                with admin_conn.cursor() as cur:
                    cur.execute(f'CREATE DATABASE "{args.db}";')
            finally:
                admin_conn.close()
            return psycopg2.connect(**conn_kwargs)


def execute_batch_insert(cur: Any, sql: str, copy_sql: str, batch: list[tuple[Any, ...]]) -> None:
    """Execute a batch of INSERT statements efficiently using COPY on psycopg or execute_batch on psycopg2."""
    if not batch:
        return
    if PG_DRIVER == "psycopg":
        with cur.copy(copy_sql) as copy:
            for row in batch:
                copy.write_row(row)
    else:
        psycopg2.extras.execute_batch(cur, sql, batch, page_size=len(batch))


def ensure_tables(cur: Any) -> None:
    """Ensure the expected PostgreSQL tables exist."""
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id char(36) not null,
            created_at timestamp(6) with time zone not null,
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
            created_at timestamp(6) with time zone not null,
            image_url varchar(2048),
            like_count bigint not null,
            updated_at timestamp(6) with time zone not null,
            primary key (id)
        );
        CREATE TABLE IF NOT EXISTS comments (
            id char(36) not null,
            author_id char(36) not null,
            content varchar(500) not null,
            created_at timestamp(6) with time zone not null,
            post_id char(36) not null,
            primary key (id)
        );
        CREATE TABLE IF NOT EXISTS likes (
            id char(36) not null,
            created_at timestamp(6) with time zone not null,
            post_id char(36) not null,
            user_id char(36) not null,
            primary key (id)
        );
        """
    )


def create_indexes(cur: Any) -> None:
    """Create secondary indexes after bulk loading for fast query execution."""
    cur.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id);
        CREATE INDEX IF NOT EXISTS idx_posts_author ON posts (author_id);
        CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at);
        ALTER TABLE likes DROP CONSTRAINT IF EXISTS idx_likes_post_user;
        DROP INDEX IF EXISTS idx_likes_post_user;
        ALTER TABLE likes ADD CONSTRAINT idx_likes_post_user UNIQUE (post_id, user_id);
        """
    )


def drop_indexes(cur: Any) -> None:
    """Temporarily drop secondary indexes before bulk insert to speed up batch writes."""
    cur.execute(
        """
        ALTER TABLE likes DROP CONSTRAINT IF EXISTS idx_likes_post_user;
        DROP INDEX IF EXISTS idx_likes_post_user;
        DROP INDEX IF EXISTS idx_comments_post;
        DROP INDEX IF EXISTS idx_posts_author;
        DROP INDEX IF EXISTS idx_posts_created_at;
        """
    )


def date_to_utc_datetime(d: datetime.date) -> datetime.datetime:
    """Convert a date from faker.date_between_dates() to a UTC datetime with a random time of day."""
    random_seconds = random.randint(0, 86399)
    return datetime.datetime.combine(
        d, datetime.time.min, tzinfo=datetime.timezone.utc
    ) + datetime.timedelta(seconds=random_seconds)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Populate Instakill PostgreSQL database with configurable synthetic test data."
    )
    parser.add_argument(
        "--db-url",
        type=str,
        default=os.environ.get("DATABASE_URL"),
        help="Optional PostgreSQL connection URI (e.g. postgresql://postgres:postgres@localhost:5432/instakill)",
    )
    parser.add_argument(
        "--host",
        type=str,
        default=os.environ.get("PGHOST", "localhost"),
        help="PostgreSQL host (default: localhost)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("PGPORT", "5432")),
        help="PostgreSQL port (default: 5432)",
    )
    parser.add_argument(
        "--db",
        type=str,
        default=os.environ.get("PGDATABASE", "instakill"),
        help="PostgreSQL database name (default: instakill)",
    )
    parser.add_argument(
        "--user",
        type=str,
        default=os.environ.get("PGUSER", "postgres"),
        help="PostgreSQL username (default: postgres)",
    )
    parser.add_argument(
        "--password",
        type=str,
        default=os.environ.get("PGPASSWORD", "postgres"),
        help="PostgreSQL password (default: postgres)",
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
        help="Batch size for bulk PostgreSQL inserts (default: 10000)",
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

    target_desc = args.db_url if args.db_url else f"{args.user}@{args.host}:{args.port}/{args.db}"
    print(f"Connecting to PostgreSQL database ({PG_DRIVER}): {target_desc}")
    start_time = time.perf_counter()

    conn = connect_postgres(args)

    # Hash "password" using bcrypt (Spring Security BCryptPasswordEncoder compatible $2a$ prefix)
    charlie_password_hash = bcrypt.hashpw(
        b"password", bcrypt.gensalt(rounds=10, prefix=b"2a")
    ).decode("utf-8")

    today = datetime.date.today()
    one_week_ago = today - datetime.timedelta(days=7)
    now_dt = datetime.datetime.now(datetime.timezone.utc)

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

    def random_past_week_dt() -> datetime.datetime:
        chosen_date = random.choice(past_week_dates)
        return date_to_utc_datetime(chosen_date)

    try:
        with conn:
            with conn.cursor() as cur:
                cur.execute("SET LOCAL synchronous_commit = OFF;")

                ensure_tables(cur)
                drop_indexes(cur)

                print("Cleaning existing comments, likes, posts, and non-(alice, bob) users...")
                cur.execute("DELETE FROM comments;")
                cur.execute("DELETE FROM likes;")
                cur.execute("DELETE FROM posts;")
                cur.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob');")

                cur.execute("SELECT username, id FROM users WHERE username IN ('alice', 'bob');")
                existing_users = {row[0]: str(row[1]).strip() for row in cur.fetchall()}

                # Ensure alice and bob exist even on a fresh database
                for reserved_name in ("alice", "bob"):
                    if reserved_name not in existing_users:
                        uid = str(uuid.uuid4())
                        cur.execute(
                            "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (%s, %s, %s, %s, %s);",
                            (uid, now_dt, f"{reserved_name}@example.com", charlie_password_hash, reserved_name),
                        )
                        existing_users[reserved_name] = uid

                # Insert charlie with bcrypt-encoded password "password"
                charlie_id = str(uuid.uuid4())
                cur.execute(
                    "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (%s, %s, %s, %s, %s);",
                    (charlie_id, now_dt, "charlie@example.com", charlie_password_hash, "charlie"),
                )
                existing_users["charlie"] = charlie_id

                print("Preserved 'alice' and 'bob'; added user 'charlie' (password='password').")

                # Step 2: Generate and batch-insert random users with unique usernames from Faker
                used_usernames = {"alice", "bob", "charlie"}
                all_user_ids: list[str] = list(existing_users.values())
                user_batch: list[tuple[str, datetime.datetime, str, str, str]] = []

                user_insert_sql = (
                    "INSERT INTO users (id, created_at, email, password_hash, username) "
                    "VALUES (%s, %s, %s, %s, %s);"
                )
                user_copy_sql = "COPY users (id, created_at, email, password_hash, username) FROM STDIN"

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
                    created_dt = random_past_week_dt()

                    all_user_ids.append(uid)
                    user_batch.append((uid, created_dt, email, charlie_password_hash, username))

                    if len(user_batch) >= args.batch_size:
                        execute_batch_insert(cur, user_insert_sql, user_copy_sql, user_batch)
                        user_batch.clear()

                if user_batch:
                    execute_batch_insert(cur, user_insert_sql, user_copy_sql, user_batch)
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

                post_batch: list[tuple[str, str, int, str, datetime.datetime, str, int, datetime.datetime]] = []
                comment_batch: list[tuple[str, str, str, datetime.datetime, str]] = []
                like_batch: list[tuple[str, datetime.datetime, str, str]] = []

                total_posts_inserted = 0
                total_comments_inserted = 0
                total_likes_inserted = 0

                post_insert_sql = """
                    INSERT INTO posts (
                        id, author_id, comment_count, content, created_at, image_url, like_count, updated_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s);
                """
                post_copy_sql = (
                    "COPY posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at) "
                    "FROM STDIN"
                )
                comment_insert_sql = """
                    INSERT INTO comments (
                        id, author_id, content, created_at, post_id
                    ) VALUES (%s, %s, %s, %s, %s);
                """
                comment_copy_sql = "COPY comments (id, author_id, content, created_at, post_id) FROM STDIN"
                like_insert_sql = """
                    INSERT INTO likes (
                        id, created_at, post_id, user_id
                    ) VALUES (%s, %s, %s, %s);
                """
                like_copy_sql = "COPY likes (id, created_at, post_id, user_id) FROM STDIN"

                def flush_batches() -> None:
                    nonlocal total_posts_inserted, total_comments_inserted, total_likes_inserted
                    if post_batch:
                        execute_batch_insert(cur, post_insert_sql, post_copy_sql, post_batch)
                        total_posts_inserted += len(post_batch)
                        post_batch.clear()

                    if comment_batch:
                        execute_batch_insert(cur, comment_insert_sql, comment_copy_sql, comment_batch)
                        total_comments_inserted += len(comment_batch)
                        comment_batch.clear()

                    if like_batch:
                        execute_batch_insert(cur, like_insert_sql, like_copy_sql, like_batch)
                        total_likes_inserted += len(like_batch)
                        like_batch.clear()

                for user_id in all_user_ids:
                    num_posts = random.randint(args.min_posts, args.max_posts)
                    for _ in range(num_posts):
                        post_id = str(uuid.uuid4())
                        post_created_dt = random_past_week_dt()
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
                                post_created_dt,
                                image_url,
                                num_likes,
                                post_created_dt,
                            )
                        )

                        # Generate comments for this post
                        for _ in range(num_comments):
                            comment_id = str(uuid.uuid4())
                            comment_author_id = random.choice(all_user_ids)
                            comment_content = random_text(args.max_words)
                            comment_created_dt = max(post_created_dt, random_past_week_dt())
                            comment_batch.append(
                                (
                                    comment_id,
                                    comment_author_id,
                                    comment_content,
                                    comment_created_dt,
                                    post_id,
                                )
                            )

                        # Generate likes for this post (unique user_ids per post)
                        if num_likes > 0:
                            liker_ids = random.sample(all_user_ids, k=num_likes)
                            for liker_id in liker_ids:
                                like_id = str(uuid.uuid4())
                                like_created_dt = max(post_created_dt, random_past_week_dt())
                                like_batch.append((like_id, like_created_dt, post_id, liker_id))

                        if (
                            len(post_batch) >= args.batch_size
                            or len(comment_batch) >= args.batch_size * 2
                            or len(like_batch) >= args.batch_size * 2
                        ):
                            flush_batches()

                flush_batches()

                print("Rebuilding indexes...")
                create_indexes(cur)
    finally:
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
