"""Add field_confidences to leads

Revision ID: 002
Revises: 001
Create Date: 2024-01-16 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '002'
down_revision: Union[str, None] = '001'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add field_confidences column to leads table
    op.add_column('leads', sa.Column('field_confidences', sa.JSON(), nullable=True))


def downgrade() -> None:
    # Remove field_confidences column from leads table
    op.drop_column('leads', 'field_confidences')
