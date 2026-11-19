"""Initial tables creation

Revision ID: 000
Revises:
Create Date: 2024-01-15 09:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '000'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Create leads table
    op.create_table(
        'leads',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('name', sa.String(length=255), nullable=True),
        sa.Column('company', sa.String(length=255), nullable=True),
        sa.Column('department', sa.String(length=255), nullable=True),
        sa.Column('job_title', sa.String(length=255), nullable=True),
        sa.Column('email', sa.String(length=255), nullable=True),
        sa.Column('phone', sa.String(length=50), nullable=True),
        sa.Column('address', sa.Text(), nullable=True),
        sa.Column('website', sa.String(length=500), nullable=True),
        sa.Column('interested_products', sa.JSON(), nullable=True),
        sa.Column('event_name', sa.String(length=255), nullable=True),
        sa.Column('booth_number', sa.String(length=100), nullable=True),
        sa.Column('collected_by', sa.String(length=255), nullable=True),
        sa.Column('collected_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('marketing_consent', sa.Boolean(), nullable=False, default=False),
        sa.Column('marketing_consent_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('status', sa.String(length=50), nullable=False),
        sa.Column('ai_extracted_data', sa.JSON(), nullable=True),
        sa.Column('modified_fields', sa.JSON(), nullable=True),
        sa.Column('card_image_url', sa.String(length=1000), nullable=True),
        sa.Column('card_image_key', sa.String(length=500), nullable=True),
        sa.Column('image_delete_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('duplicate_of_lead_id', sa.Integer(), nullable=True),
        sa.Column('crm_id', sa.String(length=255), nullable=True),
        sa.Column('crm_registered_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('crm_error_message', sa.Text(), nullable=True),
        sa.Column('client_uuid', sa.String(length=36), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.PrimaryKeyConstraint('id')
    )

    # Create indexes
    op.create_index(op.f('ix_leads_name'), 'leads', ['name'], unique=False)
    op.create_index(op.f('ix_leads_company'), 'leads', ['company'], unique=False)
    op.create_index(op.f('ix_leads_email'), 'leads', ['email'], unique=False)
    op.create_index(op.f('ix_leads_phone'), 'leads', ['phone'], unique=False)
    op.create_index(op.f('ix_leads_status'), 'leads', ['status'], unique=False)
    op.create_index(op.f('ix_leads_client_uuid'), 'leads', ['client_uuid'], unique=True)

    # Create processing_jobs table
    op.create_table(
        'processing_jobs',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('job_type', sa.String(length=50), nullable=False),
        sa.Column('status', sa.String(length=50), nullable=False),
        sa.Column('lead_id', sa.Integer(), nullable=True),
        sa.Column('retry_count', sa.Integer(), nullable=False, default=0),
        sa.Column('max_retries', sa.Integer(), nullable=False, default=3),
        sa.Column('started_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('completed_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('error_message', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.PrimaryKeyConstraint('id')
    )

    # Create indexes for processing_jobs
    op.create_index(op.f('ix_processing_jobs_status'), 'processing_jobs', ['status'], unique=False)
    op.create_index(op.f('ix_processing_jobs_lead_id'), 'processing_jobs', ['lead_id'], unique=False)


def downgrade() -> None:
    # Drop processing_jobs table
    op.drop_index(op.f('ix_processing_jobs_lead_id'), table_name='processing_jobs')
    op.drop_index(op.f('ix_processing_jobs_status'), table_name='processing_jobs')
    op.drop_table('processing_jobs')

    # Drop leads table
    op.drop_index(op.f('ix_leads_client_uuid'), table_name='leads')
    op.drop_index(op.f('ix_leads_status'), table_name='leads')
    op.drop_index(op.f('ix_leads_phone'), table_name='leads')
    op.drop_index(op.f('ix_leads_email'), table_name='leads')
    op.drop_index(op.f('ix_leads_company'), table_name='leads')
    op.drop_index(op.f('ix_leads_name'), table_name='leads')
    op.drop_table('leads')
