"""Database init script — create all tables from models."""
import click

from app import create_app
from models import db


@click.command()
@click.option("--drop", is_flag=True, help="Drop all tables first (DESTRUCTIVE).")
def init_db(drop):
    app = create_app()
    with app.app_context():
        if drop:
            click.echo("Dropping all tables...")
            db.drop_all()
        click.echo("Creating tables...")
        db.create_all()
        click.echo("Done. All tables created.")


if __name__ == "__main__":
    init_db()
