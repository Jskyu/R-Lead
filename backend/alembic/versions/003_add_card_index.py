"""Add card_index to leads (한 사진에 여러 명함)

Revision ID: 003
Revises: 002
Create Date: 2026-11-19 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = '003'
down_revision: Union[str, None] = '002'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('leads', sa.Column('card_index', sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column('leads', 'card_index')
